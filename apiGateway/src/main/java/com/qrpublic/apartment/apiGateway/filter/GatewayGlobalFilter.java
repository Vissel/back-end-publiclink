package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.ratelimit.resolver.KeyResolver;
import com.qrpublic.apartment.apiGateway.ratelimit.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;

@Slf4j
//@Component
public class GatewayGlobalFilter
//        implements GlobalFilter, Ordered
{

    //    private final RateLimitProperties rateLimitProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private KeyResolver keyResolver;
    @Autowired
    private RateLimiterService rateLimiterService;
    @Autowired
    private JwtTokenProducer jwtTokenProducer;

//    public GatewayGlobalFilter(RateLimitProperties rateLimitProperties) {
//        this.rateLimitProperties = rateLimitProperties;
//    }

//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        if (!rateLimitProperties.isEnabled()) {
//            return chain.filter(exchange);
//        }
//        String requestPath = exchange.getRequest().getPath().value();
//        if (isExcluded(requestPath)) {
//            return chain.filter(exchange);
//        }
//
//        return keyResolver.resolve(exchange)
//                .flatMap(key -> {
//                    // Here you would implement the logic to check the rate limit for the resolved key
//                    // For example, you could check Redis or an in-memory store to see if the key has exceeded its limit
//                    // If the limit is exceeded, you can return an error response
//                    // If not, you can proceed with the filter chain
//                    RateLimitPolicy policy = resolvePolicyForPath(requestPath);
//                    return rateLimiterService.isAllowed(key, policy)
//                            .flatMap(result -> handleResult(exchange, chain, result));
//                });
//    }
//
//    private Mono<? extends Void> handleResult(ServerWebExchange exchange, GatewayFilterChain chain, RateLimitResult result) {
//        if (result.isAllowed()) {
//            addRateLimitHeaders(exchange, result);
//            // After rate limiting passes, process JWT token generation
//            return processJwtTokenProduction(exchange, chain);
//        }
//        return writeRateLimitResponse(exchange, result);
//    }
//
//    private Mono<? extends Void> writeRateLimitResponse(ServerWebExchange exchange, RateLimitResult result) {
//        ServerHttpResponse response = exchange.getResponse();
//        response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
//        addRateLimitHeaders(exchange, result);
//        response.getHeaders().set(RateLimitHeaders.RETRY_AFTER, String.valueOf(result.getRetryAfterSeconds()));
//        String body = String.format("{\"message\": \"Rate limit exceeded. Try again in %d seconds.\"}", result.getRetryAfterSeconds());
//        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
//        return response.writeWith(Mono.just(buffer));
//    }
//
//    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimitResult result) {
//        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_LIMIT, String.valueOf(result.getLimit()));
//        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_REMAINING, String.valueOf(result.getRemainingTokens()));
//        exchange.getResponse().getHeaders().add(RateLimitHeaders.X_RATE_LIMIT_RESET, String.valueOf(result.getResetAtEpochSeconds()));
//    }
//
//    /**
//     * Process JWT token production for authenticated requests after rate limiting passes.
//     * This method implements banking production system standards:
//     * - Extracts user information from Authorization header
//     * - Generates/validates JWT tokens
//     * - Adds security headers and trace information
//     * - Logs audit events for compliance
//     *
//     * @param exchange ServerWebExchange
//     * @param chain GatewayFilterChain
//     * @return Mono<Void>
//     */
//    private Mono<? extends Void> processJwtTokenProduction(ServerWebExchange exchange, GatewayFilterChain chain) {
//        return Mono.fromCallable(() -> {
//            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
//            String traceId = generateTraceId();
//
//            // Add trace ID to response for audit trail
//            exchange.getResponse().getHeaders().add("X-Trace-ID", traceId);
//
//            // If Authorization header is present, validate and process token
//            if (authHeader != null && authHeader.startsWith("Bearer ")) {
//                try {
//                    String token = authHeader.substring(7);
//
//                    // Validate existing token
//                    if (jwtTokenProducer.isTokenValid(token)) {
//                        String username = jwtTokenProducer.extractSubject(token);
//                        extractRolesFromToken(token);
//
//                        log.info("Valid JWT token for user: {} | TraceID: {} | Path: {}",
//                            username, traceId, exchange.getRequest().getPath().value());
//
//                        // Add authenticated user information to request headers for downstream services
//                        exchange.getRequest().mutate()
//                                .header("X-User-ID", username)
//                                .header("X-Trace-ID", traceId)
//                                .header("X-Auth-Timestamp", String.valueOf(System.currentTimeMillis()))
//                                .build();
//
//                        // Add security headers to response
//                        exchange.getResponse().getHeaders().add("X-User-ID", username);
//                        exchange.getResponse().getHeaders().add("X-Content-Type-Options", "nosniff");
//                        exchange.getResponse().getHeaders().add("X-Frame-Options", "DENY");
//                        exchange.getResponse().getHeaders().add("X-XSS-Protection", "1; mode=block");
//
//                    } else {
//                        log.warn("Expired or invalid JWT token | TraceID: {} | Path: {}",
//                            traceId, exchange.getRequest().getPath().value());
//                    }
//                } catch (JwtException e) {
//                    log.warn("JWT token processing failed: {} | TraceID: {} | Path: {}",
//                        e.getMessage(), traceId, exchange.getRequest().getPath().value());
//                }
//            } else {
//                log.debug("No Authorization header present | TraceID: {} | Path: {}",
//                    traceId, exchange.getRequest().getPath().value());
//            }
//
//            return null;
//        }).flatMap(unused -> chain.filter(exchange))
//         .doOnError(error -> log.error("JWT processing error: ", error));
//    }
//
//    /**
//     * Extracts roles from JWT token claims.
//     * Implements production-grade error handling.
//     *
//     * @param token JWT token
//     * @return List of roles or empty list if extraction fails
//     */
//    private List<String> extractRolesFromToken(String token) {
//        try {
//            var claims = jwtTokenProducer.extractClaims(token);
//            Object rolesObj = claims.get("roles");
//            if (rolesObj instanceof List<?>) {
//                @SuppressWarnings("unchecked")
//                List<String> roles = (List<String>) rolesObj;
//                return roles;
//            }
//        } catch (Exception e) {
//            log.debug("Failed to extract roles from token: {}", e.getMessage());
//        }
//        return List.of();
//    }
//
//    /**
//     * Generates a unique trace ID for request tracking and audit purposes.
//     * Used for banking system compliance and debugging.
//     *
//     * @return UUID-based trace ID
//     */
//    private String generateTraceId() {
//        return UUID.randomUUID().toString();
//    }
//
//    private RateLimitPolicy resolvePolicyForPath(String requestPath) {
//        // Sort by order, find first matching pattern
//        return rateLimitProperties.getPolicies().stream()
//                .sorted(Comparator.comparingInt(RateLimitProperties.PolicyConfig::getOrder))
//                .filter(pc -> pathMatcher.match(pc.getPathPattern(), requestPath))
//                .map(pc -> RateLimitPolicy.builder()
//                        .capacity(pc.getCapacity())
//                        .refillToken(pc.getRefillToken())
//                        .refillPeriod(pc.getRefillPeriod())
//                        .pathPattern(pc.getPathPattern())
//                        .order(pc.getOrder())
//                        .build())
//                .findFirst()
//                .orElseGet(RateLimitPolicy::defaultPolicy);
//        // Fallback to default policy if no pattern matches
//    }
//
//    @Override
//    public int getOrder() {
//        return Ordered.HIGHEST_PRECEDENCE + 10;
//    }
//
//    private boolean isExcluded(String path) {
//        return rateLimitProperties.getExcludePaths().stream().anyMatch(excludePath ->
//                pathMatcher.match(excludePath, path));
//    }
}
