package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.adapter.ratelimit.constant.RateLimitHeaders;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitPolicy;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitResult;
import com.qrpublic.apartment.apiGateway.ratelimit.config.RateLimitProperties;
import com.qrpublic.apartment.apiGateway.ratelimit.resolver.KeyResolver;
import com.qrpublic.apartment.apiGateway.ratelimit.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

@Slf4j
@Component
public class RateLimitingWebFilter implements WebFilter, Ordered {

    private final RateLimitProperties rateLimitProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private KeyResolver keyResolver;
    @Autowired
    private RateLimiterService rateLimiterService;

    public RateLimitingWebFilter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        String requestPath = exchange.getRequest().getPath().value();
        if (isExcluded(requestPath)) {
            return chain.filter(exchange);
        }

        return keyResolver.resolve(exchange)
                .flatMap(key -> {
                    RateLimitPolicy policy = resolvePolicyForPath(requestPath);
                    return rateLimiterService.isAllowed(key, policy)
                            .flatMap(result -> handleResult(exchange, chain, result));
                });
    }

    private Mono<Void> handleResult(ServerWebExchange exchange, WebFilterChain chain, RateLimitResult result) {
        if (result.isAllowed()) {
            addRateLimitHeaders(exchange, result);
            return chain.filter(exchange);
        }
        return writeRateLimitResponse(exchange, result);
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, RateLimitResult result) {
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
