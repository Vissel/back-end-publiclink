package com.qrpublic.apartment.apiGateway.authentication;

import com.qrpublic.apartment.apiGateway.constant.FilterConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenProducer {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey.getBytes()));
    }

    /**
     * Standard generated token
     *
     * @param username
     * @param roles
     * @return
     */
    public String generateToken(String username, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(FilterConstant.ROLES_CLAIM, roles);
        claims.put(FilterConstant.TOKEN_TYPE_CLAIM, FilterConstant.ACCESS_TOKEN_TYPE);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .claim(FilterConstant.TOKEN_TYPE_CLAIM, FilterConstant.REFRESH_TOKEN_TYPE)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Custom generated token with valid time
     *
     * @param username
     * @param role
     * @param validTime
     * @return
     */
    public String generateTokenByValidTime(String username, String role, long validTime, String name) {
        return Jwts.builder().setSubject(username).claim("role", role).claim("name", name).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + validTime))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey.getBytes())),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody();
    }

    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractClaims(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isHeaderTokenValid(String headerAuthorization) throws JwtException {
        final String token = headerAuthorization.substring(FilterConstant.BEARER_PREFIX.length());
        if (token != null && isTokenValid(token)) {
            return !extractClaims(token).getExpiration().before(new Date());
        }
        return false;
    }

    /**
     * Extract username from token with production-grade error handling
     *
     * @param token
     * @return
     */
    public Mono<String> getUsernameFromToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                String username = extractClaim(token, Claims::getSubject);
                if (username == null) {
                    log.warn("Username claim is null in token");
                    throw new JwtException("Username claim not found in token");
                }
                return username;
            } catch (Exception e) {
                log.error("Failed to extract username from token: {}", e.getMessage());
                throw new JwtException("Failed to extract username: " + e.getMessage(), e);
            }
        });
    }

    public Mono<List<String>> getRolesFromToken(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = extractAllClaims(token);
            return claims.get(FilterConstant.ROLES_CLAIM, List.class);
        });
    }

    /**
     * Validate token by checking expiration
     *
     * @param token
     * @return
     */
    public Mono<Boolean> validateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = extractAllClaims(token);
                return !claims.getExpiration().before(new Date());
            } catch (Exception e) {
                log.error("Token validation failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Validate refresh token with type checking
     * Banking security: Ensure token type is 'refresh' before allowing rotation
     *
     * @param token The refresh token to validate
     * @return Mono<Boolean> true if valid refresh token
     */
    public Mono<Boolean> validateRefreshToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = extractAllClaims(token);
                
                // Check expiration
                if (claims.getExpiration().before(new Date())) {
                    log.warn("Refresh token expired");
                    return false;
                }
                
                // Check token type
                String tokenType = claims.get(FilterConstant.TOKEN_TYPE_CLAIM, String.class);
                if (!FilterConstant.REFRESH_TOKEN_TYPE.equals(tokenType)) {
                    log.warn("Invalid token type for refresh: {}", tokenType);
                    return false;
                }
                
                return true;
            } catch (Exception e) {
                log.error("Refresh token validation failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Extract username from refresh token with validation
     *
     * @param token The refresh token
     * @return Mono<String> username or error
     */
    public Mono<String> extractUsernameFromRefreshToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = extractAllClaims(token);
                
                // Validate token type
                String tokenType = claims.get(FilterConstant.TOKEN_TYPE_CLAIM, String.class);
                if (!FilterConstant.REFRESH_TOKEN_TYPE.equals(tokenType)) {
                    throw new JwtException("Invalid token type: expected refresh token");
                }
                
                String username = claims.getSubject();
                if (username == null || username.isBlank()) {
                    throw new JwtException("Username claim not found in refresh token");
                }
                
                return username;
            } catch (JwtException e) {
                throw e;
            } catch (Exception e) {
                throw new JwtException("Failed to extract username from refresh token: " + e.getMessage(), e);
            }
        });
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
