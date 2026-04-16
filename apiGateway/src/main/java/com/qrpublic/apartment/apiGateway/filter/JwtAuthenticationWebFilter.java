package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.constant.FilterConstant;
import io.jsonwebtoken.lang.Assert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationWebFilter implements WebFilter, Ordered {

    @Autowired
    private JwtTokenProducer jwtTokenProducer;

    //    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        if (duplicatedRequest(exchange)) {
//            log.warn("Duplicate request detected | TraceID: {} | Path: {}",
//                    generateTraceId(), exchange.getRequest().getPath().value());
//            return exchange.getResponse().setComplete();
//        }
        String traceId = generateTraceId();
        try {
            String authHeader = exchange.getRequest().getHeaders().getFirst(FilterConstant.AUTHORIZATION_HEADER);
            Assert.isTrue(authHeader != null && authHeader.startsWith(FilterConstant.BEARER_PREFIX));

            String token = authHeader.substring(FilterConstant.BEARER_PREFIX.length());

            if (jwtTokenProducer.isTokenValid(token)) {
                // Use flatMap to handle the async username extraction reactively
                return jwtTokenProducer.getUsernameFromToken(token).doOnNext(username -> log.debug("Successfully extracted username: {} | TraceID: {}", username, traceId)).flatMap(username -> {
                    try {
                        List<String> roles = extractRolesFromToken(token);

                        if (roles.isEmpty()) {
                            log.warn("No roles found for user: {} | TraceID: {} | Path: {}", username, traceId, exchange.getRequest().getPath().value());
                            return chain.filter(exchange);
                        }

                        // Create authentication object
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));

                        // Set authentication in security context
                        SecurityContext context = new SecurityContextImpl(authentication);

                        log.info("Valid JWT token for user: {} | TraceID: {} | Path: {} | Roles: {}", username, traceId, exchange.getRequest().getPath().value(), roles);

                        // Add authenticated user information to request headers for downstream services
                        exchange.getRequest().mutate().header(FilterConstant.USER_ID_HEADER, username).header(FilterConstant.TRACE_ID_HEADER, traceId).header(FilterConstant.AUTH_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis())).build();

                        // Add security headers to response
                        exchange.getResponse().getHeaders().add(FilterConstant.USER_ID_HEADER, username);
                        exchange.getResponse().getHeaders().add(FilterConstant.CONTENT_TYPE_OPTIONS_HEADER, FilterConstant.CONTENT_TYPE_OPTIONS_VALUE);
                        exchange.getResponse().getHeaders().add(FilterConstant.FRAME_OPTIONS_HEADER, FilterConstant.FRAME_OPTIONS_VALUE);
                        exchange.getResponse().getHeaders().add(FilterConstant.XSS_PROTECTION_HEADER, FilterConstant.XSS_PROTECTION_VALUE);

                        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    } catch (Exception e) {
                        log.error("Error processing roles for user: {} | TraceID: {} | Error: {}", username, traceId, e.getMessage());
                        return chain.filter(exchange);
                    }
                }).onErrorResume(e -> {
                    log.warn("Failed to extract username from token | TraceID: {} | Path: {} | Error: {}", traceId, exchange.getRequest().getPath().value(), e.getMessage());
                    return chain.filter(exchange);
                });
            } else {
                log.warn("Expired or invalid JWT token | TraceID: {} | Path: {}", traceId, exchange.getRequest().getPath().value());
                return chain.filter(exchange);
            }
        } catch (Exception e) {
            log.debug("No Authorization header present | TraceID: {} | Path: {}", traceId, exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        } finally {
            // Add trace ID to response for audit trail
            exchange.getResponse().getHeaders().add(FilterConstant.TRACE_ID_HEADER, traceId);
        }

    }

    private boolean duplicatedRequest(ServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().containsKey(FilterConstant.TRACE_ID_HEADER) && exchange.getResponse().getHeaders().containsKey(FilterConstant.USER_ID_HEADER);
    }

    /**
     * Extracts roles from JWT token claims.
     * Implements production-grade error handling.
     *
     * @param token JWT token
     * @return List of roles or empty list if extraction fails
     */
    private List<String> extractRolesFromToken(String token) {
        try {
            var claims = jwtTokenProducer.extractClaims(token);
            Object rolesObj = claims.get(FilterConstant.ROLES_CLAIM);
            if (rolesObj instanceof List<?>) {
                @SuppressWarnings("unchecked") List<String> roles = (List<String>) rolesObj;
                return roles;
            }
        } catch (Exception e) {
            log.debug("Failed to extract roles from token: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Generates a unique trace ID for request tracking and audit purposes.
     * Used for banking system compliance and debugging.
     *
     * @return UUID-based trace ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10; // After rate limiting
    }

    /**
     * Sends a 401 Unauthorized response with error details
     */
    private Mono<Void> sendUnauthorized(ServerWebExchange exchange, String errorMessage, String traceId) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Error-Message", errorMessage);
        exchange.getResponse().getHeaders().add(FilterConstant.TRACE_ID_HEADER, traceId);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String jsonResponse = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\",\"traceId\":\"%s\"}", errorMessage, traceId);

        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes())));
    }
}
