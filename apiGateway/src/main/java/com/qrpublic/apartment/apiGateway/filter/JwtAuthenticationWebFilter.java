package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String traceId = generateTraceId();

        // Add trace ID to response for audit trail
        exchange.getResponse().getHeaders().add("X-Trace-ID", traceId);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtTokenProducer.isTokenValid(token)) {
                    String username = jwtTokenProducer.extractSubject(token);
                    List<String> roles = extractRolesFromToken(token);

                    // Create authentication object
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                            );

                    // Set authentication in security context
                    SecurityContext context = new SecurityContextImpl(authentication);

                    log.info("Valid JWT token for user: {} | TraceID: {} | Path: {} | Roles: {}",
                            username, traceId, exchange.getRequest().getPath().value(), roles);

                    // Add authenticated user information to request headers for downstream services
                    exchange.getRequest().mutate()
                            .header("X-User-ID", username)
                            .header("X-Trace-ID", traceId)
                            .header("X-Auth-Timestamp", String.valueOf(System.currentTimeMillis()))
                            .build();

                    // Add security headers to response
                    exchange.getResponse().getHeaders().add("X-User-ID", username);
                    exchange.getResponse().getHeaders().add("X-Content-Type-Options", "nosniff");
                    exchange.getResponse().getHeaders().add("X-Frame-Options", "DENY");
                    exchange.getResponse().getHeaders().add("X-XSS-Protection", "1; mode=block");

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                } else {
                    log.warn("Expired or invalid JWT token | TraceID: {} | Path: {}",
                            traceId, exchange.getRequest().getPath().value());
                }
            } catch (JwtException e) {
                log.warn("JWT token processing failed: {} | TraceID: {} | Path: {}",
                        e.getMessage(), traceId, exchange.getRequest().getPath().value());
            }
        } else {
            log.debug("No Authorization header present | TraceID: {} | Path: {}",
                    traceId, exchange.getRequest().getPath().value());
        }

        // If no valid token, continue without authentication
        return chain.filter(exchange);
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
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) rolesObj;
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
}
