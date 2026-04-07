package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.adapter.ratelimit.constant.RateLimitHeaders;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitPolicy;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitResult;
import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.ratelimit.config.RateLimitProperties;
import com.qrpublic.apartment.apiGateway.ratelimit.resolver.KeyResolver;
import com.qrpublic.apartment.apiGateway.ratelimit.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

@Slf4j
@Component
public class GatewayGlobalFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties rateLimitProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private KeyResolver keyResolver;
    @Autowired
    private RateLimiterService rateLimiterService;
    @Autowired
    private JwtTokenProducer jwtTokenProducer;

    public GatewayGlobalFilter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        String requestPath = exchange.getRequest().getPath().value();
        if (isExcluded(requestPath)) {
            return chain.filter(exchange);
        }

        return keyResolver.resolve(exchange)
                .flatMap(key -> {
                    ;
                    // Here you would implement the logic to check the rate limit for the resolved key
                    // For example, you could check Redis or an in-memory store to see if the key has exceeded its limit
                    // If the limit is exceeded, you can return an error response
                    // If not, you can proceed with the filter chain
                    RateLimitPolicy policy = resolvePolicyForPath(requestPath);
                    return rateLimiterService.isAllowed(key, policy)
                            .flatMap(result -> handleResult(exchange, chain, result));
                });
    }

    private Mono<? extends Void> handleResult(ServerWebExchange exchange, GatewayFilterChain chain, RateLimitResult result) {
        if (result.isAllowed()) {
            addRateLimitHeaders(exchange, result);
            return chain.filter(exchange);
        }
        return writeRateLimitResponse(exchange, result);
    }

    private Mono<? extends Void> writeRateLimitResponse(ServerWebExchange exchange, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        addRateLimitHeaders(exchange, result);
        response.getHeaders().set(RateLimitHeaders.RETRY_AFTER, String.valueOf(result.getRetryAfterSeconds()));
        String body = String.format("{\"message\": \"Rate limit exceeded. Try again in %d seconds.\"}", result.getRetryAfterSeconds());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimitResult result) {
        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_LIMIT, String.valueOf(result.getLimit()));
        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_REMAINING, String.valueOf(result.getRemainingTokens()));
        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_RESET, String.valueOf(result.getResetAtEpochSeconds()));
    }

    private RateLimitPolicy resolvePolicyForPath(String requestPath) {
        // Sort by order, find first matching pattern
        return rateLimitProperties.getPolicies().stream()
                .sorted(Comparator.comparingInt(RateLimitProperties.PolicyConfig::getOrder))
                .filter(pc -> pathMatcher.match(pc.getPathPattern(), requestPath))
                .map(pc -> RateLimitPolicy.builder()
                        .capacity(pc.getCapacity())
                        .refillToken(pc.getRefillToken())
                        .refillPeriod(pc.getRefillPeriod())
                        .pathPattern(pc.getPathPattern())
                        .order(pc.getOrder())
                        .build())
                .findFirst()
                .orElseGet(RateLimitPolicy::defaultPolicy);
        // Fallback to default policy if no pattern matches
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isExcluded(String path) {
        return rateLimitProperties.getExcludePaths().stream().anyMatch(excludePath ->
                pathMatcher.match(excludePath, path));
    }
}
