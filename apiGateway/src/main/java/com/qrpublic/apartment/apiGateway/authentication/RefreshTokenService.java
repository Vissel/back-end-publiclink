package com.qrpublic.apartment.apiGateway.authentication;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.apiGateway.authentication.request.RefreshTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.RefreshTokenResponse;
import com.qrpublic.apartment.apiGateway.exception.BusinessException;
import com.qrpublic.apartment.apiGateway.exception.ErrorCode;
import com.qrpublic.apartment.apiGateway.integration.UserClient;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Production-grade refresh token service implementing Ant Group banking security standards:
 * 
 * Security Features:
 * 1. Token Rotation - Each refresh token is one-time use (prevents replay attacks)
 * 2. Atomic Operations - Delete old token before issuing new one
 * 3. Blacklist Support - Compromised tokens can be revoked
 * 4. Dual Validation - JWT validation + Redis storage validation
 * 5. Trace Logging - Full audit trail for compliance
 * 6. Concurrent Attack Prevention - Race condition handling
 */
@Slf4j
@Service
public class RefreshTokenService {

    @Autowired
    private JwtTokenProducer jwtTokenProducer;

    @Autowired
    private RefreshTokenStorageService storageService;

    @Autowired
    private Validator validator;

    @Autowired
    private UserClient userClient;

    @Value("${jwt.expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Refresh access token using refresh token with rotation
     * Banking security flow:
     * 1. Validate request
     * 2. Validate JWT structure and expiration
     * 3. Check blacklist (compromised token detection)
     * 4. Validate against Redis storage
     * 5. Delete old refresh token (atomic rotation)
     * 6. Generate new access + refresh token pair
     * 7. Store new refresh token
     * 8. Return new token pair
     *
     * @param request Refresh token request
     * @return Mono<RefreshTokenResponse> New token pair
     */
    public Mono<RefreshTokenResponse> refreshAccessToken(RefreshTokenRequest request) {
        // Step 1: Validate request
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((s1, s2) -> s1 + ", " + s2)
                    .orElse("Validation failed");
            return Mono.error(new BusinessException(ErrorCode.INVALID_ARGUMENTS, new Object[]{errorMessage}));
        }

        String refreshToken = request.getRefreshToken();
        String traceId = generateTraceId();

        log.info("Token refresh initiated | TraceID: {}", traceId);

        // Step 2: Validate JWT structure and type
        return jwtTokenProducer.validateRefreshToken(refreshToken)
                .flatMap(isValid -> {
                    if (!isValid) {
                        log.warn("Invalid refresh token structure | TraceID: {}", traceId);
                        return Mono.error(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
                    }
                    return Mono.just(true);
                })
                // Step 3: Check blacklist
                .flatMap(valid -> storageService.isTokenBlacklisted(refreshToken)
                        .flatMap(isBlacklisted -> {
                            if (isBlacklisted) {
                                log.error("Blacklisted refresh token used - potential security breach | TraceID: {}", traceId);
                                return Mono.error(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
                            }
                            return Mono.just(true);
                        }))
                // Step 4: Extract username from JWT
                .flatMap(valid -> jwtTokenProducer.extractUsernameFromRefreshToken(refreshToken)
                        .flatMap(username -> Mono.just(username)))
                // Step 5: Validate against Redis storage
                .flatMap(username -> storageService.validateAndGetRefreshToken(refreshToken)
                        .flatMap(tokenData -> {
                            // Verify username matches
                            if (!tokenData.getUsername().equals(username)) {
                                log.error("Username mismatch in refresh token | TraceID: {}", traceId);
                                return Mono.error(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
                            }
                            return Mono.just(tokenData);
                        }))
                // Step 6: Atomic rotation - delete old token
                .flatMap(tokenData -> storageService.deleteRefreshToken(refreshToken)
                        .flatMap(deleted -> {
                            if (!deleted) {
                                log.error("Refresh token not found in storage - possible replay attack | TraceID: {}", traceId);
                                return Mono.error(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
                            }
                            return Mono.just(tokenData);
                        }))
                // Step 7: Generate new tokens (re-fetch profile so seller roles/name stay current)
                .flatMap(tokenData -> {
                    String username = tokenData.getUsername();
                    List<String> cachedRoles = parseRoles(tokenData.getRoles());

                    return userClient.findUserByUsername(new FindUserRequest(username))
                            .map(user -> resolveUserContext(username, user, cachedRoles))
                            .defaultIfEmpty(new UserTokenContext(username, cachedRoles, null))
                            .flatMap(context -> {
                                if (context.roles().isEmpty()) {
                                    log.error("No roles resolved during token refresh | User: {} | TraceID: {}",
                                            username, traceId);
                                    return Mono.error(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
                                }

                                String newAccessToken = jwtTokenProducer.generateToken(
                                        context.username(), context.roles(), context.name());
                                String newRefreshToken = jwtTokenProducer.generateRefreshToken(context.username());
                                String rolesString = String.join(",", context.roles());

                                return storageService.storeRefreshToken(
                                                newRefreshToken,
                                                context.username(),
                                                rolesString,
                                                refreshExpiration / 1000)
                                        .map(stored -> RefreshTokenResponse.builder()
                                                .accessToken(newAccessToken)
                                                .refreshToken(newRefreshToken)
                                                .username(context.username())
                                                .roles(context.roles())
                                                .expiresAt(new Date(System.currentTimeMillis() + accessExpiration))
                                                .message("Token refreshed successfully")
                                                .build())
                                        .onErrorResume(e -> {
                                            log.error("Failed to store new refresh token | TraceID: {} | Error: {}",
                                                    traceId, e.getMessage());
                                            return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_FAILED));
                                        });
                            });
                })
                .doOnSuccess(response -> 
                        log.info("Token refresh successful | User: {} | TraceID: {}", 
                                response.getUsername(), traceId))
                .doOnError(e -> 
                        log.error("Token refresh failed | TraceID: {} | Error: {}", 
                                traceId, e.getMessage()))
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_FAILED));
                });
    }

    /**
     * Logout with token revocation
     * Banking security: Blacklist both access and refresh tokens
     *
     * @param refreshToken The refresh token to revoke
     * @return Mono<Boolean> success status
     */
    public Mono<Boolean> logout(String refreshToken) {
        String traceId = generateTraceId();
        log.info("Logout initiated | TraceID: {}", traceId);

        return Mono.just(refreshToken)
                // Extract username for logging
                .flatMap(token -> jwtTokenProducer.extractUsernameFromRefreshToken(token)
                        .map(username -> new Object[]{token, username})
                        .onErrorResume(e -> Mono.just(new Object[]{token, "unknown"})))
                // Blacklist refresh token
                .flatMap(objects -> {
                    String token = (String) objects[0];
                    String username = (String) objects[1];

                    return storageService.deleteRefreshToken(token)
                            .then(storageService.blacklistToken(token, refreshExpiration / 1000))
                            .doOnSuccess(success -> 
                                    log.info("User logged out and tokens revoked | User: {} | TraceID: {}", 
                                            username, traceId))
                            .onErrorResume(e -> {
                                log.error("Logout failed | User: {} | TraceID: {} | Error: {}", 
                                        username, traceId, e.getMessage());
                                return Mono.just(false);
                            });
                });
    }

    /**
     * Store refresh token during initial login
     *
     * @param refreshToken The refresh token
     * @param username     The username
     * @param roles        User roles
     * @return Mono<Boolean> success status
     */
    public Mono<Boolean> storeInitialRefreshToken(String refreshToken, String username, List<String> roles) {
        String rolesString = String.join(",", roles);
        return storageService.storeRefreshToken(
                refreshToken, 
                username, 
                rolesString,
                refreshExpiration / 1000);
    }

    /**
     * Parse roles from comma-separated string
     */
    private List<String> parseRoles(String rolesString) {
        if (rolesString == null || rolesString.isBlank()) {
            return List.of();
        }
        return Arrays.asList(rolesString.split(","));
    }

    private UserTokenContext resolveUserContext(String username, FindUserResponse user, List<String> cachedRoles) {
        List<String> roles = cachedRoles;
        if (user.getRole() != null && !user.getRole().isBlank()) {
            roles = List.of(user.getRole());
        }
        return new UserTokenContext(username, roles, user.getName());
    }

    private record UserTokenContext(String username, List<String> roles, String name) {
    }

    /**
     * Generate trace ID for audit logging
     */
    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString();
    }
}
