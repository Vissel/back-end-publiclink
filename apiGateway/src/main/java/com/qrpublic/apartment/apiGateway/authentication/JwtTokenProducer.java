package com.qrpublic.apartment.apiGateway.authentication;

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
        claims.put("roles", roles);
        claims.put("type", "access");
        return Jwts.builder()
                .setSubject(username)
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("type", "refresh")
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
    public String generateTokenByValidTime(String username, String role, long validTime) {
        return Jwts.builder().setSubject(username).claim("role", role).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + validTime))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey.getBytes())),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(getKey()))).build()
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
        final String token = headerAuthorization.substring(7);
        if (token != null && isTokenValid(token)) {
            return !extractClaims(token).getExpiration().before(new Date());
        }
        return false;
    }

    protected byte[] getKey() {
        return this.secretKey.getBytes();
    }

    public Mono<String> getUsernameFromToken(String token) {
        return Mono.fromCallable(() -> extractClaim(token, Claims::getSubject));
    }

    public Mono<List<String>> getRolesFromToken(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = extractAllClaims(token);
            return claims.get("roles", List.class);
        });
    }

    /**
     * Validate token by checking type and expiration
     *
     * @param token
     * @return
     */
    public Mono<Boolean> validateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = extractAllClaims(token);
                boolean isAccessToken = "access".equals(claims.get("type"));
                boolean isExpired = claims.getExpiration().before(new Date());
                return isAccessToken && !isExpired;
            } catch (Exception e) {
                log.error("Token validation failed: {}", e.getMessage());
                return false;
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
