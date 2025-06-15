package com.qrpublic.apartment.config;

import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Primary
@Component
public class JwtUtil {

	@Value("${jwt.secret}")
	private String secretKey;

	private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000; // 15 minutes

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
				.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey.getBytes())),
						SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * Custom generated token withh valid time
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

	protected byte[] getKey() {
		return this.secretKey.getBytes();
	}
}
