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
        String traceId = generateTraceId();
        // Identity headers are never accepted from the wire. The previous version
        // built a mutated request and discarded it, so the gateway forwarded
        // nothing and downstream services trusted whatever X-User-ID the browser
        // sent - spoofable on every permit-all route. Strip what arrived first,
        // then re-attach the values only from a validated JWT.
        final ServerWebExchange cleaned = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(FilterConstant.USER_ID_HEADER);
                    headers.remove(FilterConstant.TRACE_ID_HEADER);
                    headers.remove(FilterConstant.AUTH_TIMESTAMP_HEADER);
                }))
                .build();
        try {
            String authHeader = cleaned.getRequest().getHeaders().getFirst(FilterConstant.AUTHORIZATION_HEADER);
            Assert.isTrue(authHeader != null && authHeader.startsWith(FilterConstant.BEARER_PREFIX));

            String token = authHeader.substring(FilterConstant.BEARER_PREFIX.length());

            if (jwtTokenProducer.isTokenValid(token)) {
                // Use flatMap to handle the async username extraction reactively
                return jwtTokenProducer.getUsernameFromToken(token).doOnNext(username -> log.debug("Successfully extracted username: {} | TraceID: {}", username, traceId)).flatMap(username -> {
                    try {
                        // Banking security: Validate token type is 'access' (not 'refresh')
                        var claims = jwtTokenProducer.extractClaims(token);
                        String tokenType = claims.get(FilterConstant.TOKEN_TYPE_CLAIM, String.class);
                        
                        if (!FilterConstant.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                            log.warn("Invalid token type for authentication: {} | Expected: access | TraceID: {} | Path: {}", 
                                    tokenType, traceId, cleaned.getRequest().getPath().value());
                            return sendUnauthorized(cleaned, "Invalid token type. Access token required.", traceId);
                        }
                        
                        List<String> roles = extractRolesFromToken(token);

                        if (roles.isEmpty()) {
                            log.warn("No roles found for user: {} | TraceID: {} | Path: {}", username, traceId, cleaned.getRequest().getPath().value());
                            return chain.filter(cleaned);
                        }

                        // Create authentication object
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));

                        // Set authentication in security context
                        SecurityContext context = new SecurityContextImpl(authentication);

                        log.info("Valid JWT token for user: {} | TraceID: {} | Path: {} | Roles: {}", username, traceId, cleaned.getRequest().getPath().value(), roles);

                        // Forward the authenticated identity downstream. Mutating the
                        // request alone is not enough: the new request has to travel to
                        // the chain inside a mutated exchange.
                        ServerWebExchange forwarded = cleaned.mutate()
                                .request(request -> request
                                        .header(FilterConstant.USER_ID_HEADER, username)
                                        .header(FilterConstant.TRACE_ID_HEADER, traceId)
                                        .header(FilterConstant.AUTH_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis())))
                                .build();

                        // Add security headers to response
                        forwarded.getResponse().getHeaders().add(FilterConstant.USER_ID_HEADER, username);
                        forwarded.getResponse().getHeaders().add(FilterConstant.CONTENT_TYPE_OPTIONS_HEADER, FilterConstant.CONTENT_TYPE_OPTIONS_VALUE);
                        forwarded.getResponse().getHeaders().add(FilterConstant.FRAME_OPTIONS_HEADER, FilterConstant.FRAME_OPTIONS_VALUE);
                        forwarded.getResponse().getHeaders().add(FilterConstant.XSS_PROTECTION_HEADER, FilterConstant.XSS_PROTECTION_VALUE);

                        return chain.filter(forwarded).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    } catch (Exception e) {
                        log.error("Error processing roles for user: {} | TraceID: {} | Error: {}", username, traceId, e.getMessage());
                        return chain.filter(cleaned);
                    }
                }).onErrorResume(e -> {
                    log.warn("Failed to extract username from token | TraceID: {} | Path: {} | Error: {}", traceId, cleaned.getRequest().getPath().value(), e.getMessage());
                    return chain.filter(cleaned);
                });
            } else {
                log.warn("Expired or invalid JWT token | TraceID: {} | Path: {}", traceId, cleaned.getRequest().getPath().value());
                return chain.filter(cleaned);
            }
        } catch (Exception e) {
            log.debug("No Authorization header present | TraceID: {} | Path: {}", traceId, cleaned.getRequest().getPath().value());
            return chain.filter(cleaned);
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
            if (rolesObj instanceof List<?> roles && !roles.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> roleList = (List<String>) roles;
                return roleList;
            }
            String role = claims.get("role", String.class);
            if (role != null && !role.isBlank()) {
                return List.of(role);
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
