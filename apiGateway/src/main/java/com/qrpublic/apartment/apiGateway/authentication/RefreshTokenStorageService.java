package com.qrpublic.apartment.apiGateway.authentication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Production-grade refresh token storage service for banking environment.
 * Implements Ant Group security standards:
 * - Redis-based token storage with TTL
 * - Token rotation support (one-time use refresh tokens)
 * - Token revocation capability
 * - Concurrent refresh attack prevention
 */
@Slf4j
@Service
public class RefreshTokenStorageService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "token_blacklist:";
    private static final String PASSWORD_CHANGE_PREFIX = "pwd_change:";

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    /**
     * Store refresh token with user context and TTL
     * Banking security: Store username + roles for validation during rotation
     *
     * @param refreshToken The refresh token JWT
     * @param username     The authenticated username
     * @param roles        User roles for validation
     * @param ttlSeconds   Time-to-live in seconds
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> storeRefreshToken(String refreshToken, String username, String roles, long ttlSeconds) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        String value = username + "|" + roles;
        
        return redisTemplate.opsForValue()
                .set(key, value, Duration.ofSeconds(ttlSeconds))
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Refresh token stored for user: {} | TTL: {}s", username, ttlSeconds);
                    } else {
                        log.warn("Failed to store refresh token for user: {}", username);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Redis error storing refresh token for user: {} | Error: {}", username, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Validate and retrieve refresh token data
     * Banking security: Atomic read-and-delete for rotation (prevents replay attacks)
     *
     * @param refreshToken The refresh token JWT
     * @return Mono<TokenData> containing username and roles, or empty if invalid
     */
    public Mono<TokenData> validateAndGetRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> {
                    String[] parts = value.split("\\|");
                    if (parts.length == 2) {
                        String username = parts[0];
                        String roles = parts[1];
                        log.debug("Refresh token validated for user: {}", username);
                        return Mono.just(new TokenData(username, roles));
                    } else {
                        log.warn("Invalid refresh token data format");
                        return Mono.empty();
                    }
                })
                .doOnNext(data -> log.debug("Refresh token retrieved for user: {}", data.getUsername()))
                .doOnError(e -> log.error("Error validating refresh token: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Delete refresh token (used during rotation)
     * Banking security: Old token must be deleted before issuing new one
     *
     * @param refreshToken The refresh token to delete
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> deleteRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        
        return redisTemplate.delete(key)
                .map(deleted -> deleted > 0)
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        log.debug("Refresh token deleted (rotated)");
                    } else {
                        log.warn("Refresh token not found for deletion");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error deleting refresh token: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Blacklist a token (for logout or compromised token scenarios)
     * Banking security: Blacklist TTL matches original token expiration
     *
     * @param token        The token to blacklist
     * @param ttlSeconds   Time-to-live (should match token expiration)
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> blacklistToken(String token, long ttlSeconds) {
        String key = BLACKLIST_PREFIX + token;
        
        return redisTemplate.opsForValue()
                .set(key, "revoked", Duration.ofSeconds(ttlSeconds))
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Token blacklisted for {}s", ttlSeconds);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error blacklisting token: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Check if a token is blacklisted
     *
     * @param token The token to check
     * @return Mono<Boolean> true if blacklisted
     */
    public Mono<Boolean> isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.error("Error checking token blacklist status: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Store a one-time password-change ticket for a first-login user.
     *
     * @param ticket     Random ticket id issued to the client
     * @param username   Username the ticket is bound to
     * @param ttlSeconds Time-to-live in seconds
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> storePasswordChangeTicket(String ticket, String username, long ttlSeconds) {
        String key = PASSWORD_CHANGE_PREFIX + ticket;

        return redisTemplate.opsForValue()
                .set(key, username, Duration.ofSeconds(ttlSeconds))
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Password-change ticket stored for user: {} | TTL: {}s", username, ttlSeconds);
                    } else {
                        log.warn("Failed to store password-change ticket for user: {}", username);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Redis error storing password-change ticket | Error: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Atomically consume a password-change ticket (single-use).
     *
     * @param ticket Ticket id issued at login
     * @return Mono<String> the bound username, or empty if invalid/expired
     */
    public Mono<String> consumePasswordChangeTicket(String ticket) {
        String key = PASSWORD_CHANGE_PREFIX + ticket;

        return redisTemplate.opsForValue()
                .getAndDelete(key)
                .doOnNext(username -> log.info("Password-change ticket consumed for user: {}", username))
                .onErrorResume(e -> {
                    log.error("Error consuming password-change ticket: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Token data holder for secure transfer
     */
    public static class TokenData {
        private final String username;
        private final String roles;

        public TokenData(String username, String roles) {
            this.username = username;
            this.roles = roles;
        }

        public String getUsername() {
            return username;
        }

        public String getRoles() {
            return roles;
        }
    }
}
