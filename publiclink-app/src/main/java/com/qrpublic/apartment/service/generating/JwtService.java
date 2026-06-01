package com.qrpublic.apartment.service.generating;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Primary
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000; // 15 minutes

    private static final int INDEX_AFTER_BEARER = 7;

    /**
     * Standard generated token
     *
     * @param username
     * @param role
     * @return
     */
    public String generateToken(String username, String role) {
        return Jwts.builder().setSubject(username).claim("role", role).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()),
                        SignatureAlgorithm.HS256)
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
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract claims from token
     *
     * @param token
     * @return
     */
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(getKey())).build()
                .parseClaimsJws(token).getBody();
    }

    /**
     * Extract claim by key
     *
     * @param token
     * @param key
     * @return
     */
    public Object extractClaimByKey(String token, String key) {
        return extractClaims(token).get(key);
    }

    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            Date expiration = claims.getExpiration();
            Date now = new Date();
            boolean isValid = !expiration.before(now);
            
            if (!isValid) {
                log.warn("Token expired. Expiration: {}, Current time: {}", expiration, now);
            } else {
                log.debug("Token is valid. Expires at: {}", expiration);
            }
            
            return isValid;
        } catch (JwtException e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during token validation: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean isHeaderTokenValid(String headerAuthorization) throws JwtException {
        final String token = headerAuthorization.substring(INDEX_AFTER_BEARER);
        if (token != null && isTokenValid(token)) {
            return !extractClaims(token).getExpiration().before(new Date());
        }
        return false;
    }

    protected byte[] getKey() {
        // Return raw bytes - subclasses can override for different keys
        // Do NOT apply Base64 decoding here
        return this.secretKey.getBytes();
    }
}
