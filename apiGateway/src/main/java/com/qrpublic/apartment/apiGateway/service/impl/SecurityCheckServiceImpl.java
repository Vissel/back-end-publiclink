package com.qrpublic.apartment.apiGateway.service.impl;

import com.qrpublic.apartment.adapter.authentication.response.TokenClaimsResponse;
import com.qrpublic.apartment.adapter.user.request.ExtendAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.authentication.request.AuthenticatedTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.AuthenticatedTokenResponse;
import com.qrpublic.apartment.apiGateway.constant.FilterConstant;
import com.qrpublic.apartment.apiGateway.service.SecurityCheckService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;

@Service
public class SecurityCheckServiceImpl implements SecurityCheckService {

    private static final Logger log = LoggerFactory.getLogger(SecurityCheckServiceImpl.class);

    @Autowired
    JwtTokenProducer jwtTokenProducer;

    @Override
    public Mono<Boolean> checkValidToken(String token) {
        return jwtTokenProducer.validateToken(token);
    }

    @Override
    public Mono<TokenClaimsResponse> extractTokenClaims(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = jwtTokenProducer.extractClaims(token);
                TokenClaimsResponse response = new TokenClaimsResponse();
                response.setUsername(claims.getSubject());
                response.setName(claims.get("name", String.class));

                String role = claims.get("role", String.class);
                if (role == null) {
                    Object rolesObj = claims.get(FilterConstant.ROLES_CLAIM);
                    if (rolesObj instanceof List<?> roles && !roles.isEmpty()) {
                        role = String.valueOf(roles.get(0));
                    }
                }
                response.setRole(role);
                return response;
            } catch (Exception e) {
                log.error("Failed to extract token claims: {}", e.getMessage());
                throw new RuntimeException("Failed to extract token claims", e);
            }
        }).onErrorResume(throwable -> Mono.error(new RuntimeException("Token claims extraction failed", throwable)));
    }

    @Override
    public Mono<AuthenticatedTokenResponse> generateAuthenticationLink(
            AuthenticatedTokenRequest authenticatedTokenRequest) {
        return Mono.fromCallable(() -> {
            try {
                String authenticatedToken = jwtTokenProducer.generateTokenByValidTime(
                        authenticatedTokenRequest.getUsername(),
                        authenticatedTokenRequest.getRole(), authenticatedTokenRequest.getValidTime(),
                        authenticatedTokenRequest.getName());
                Date issueAt = new Date();
                Date validTime = new Date(issueAt.getTime() + authenticatedTokenRequest.getValidTime());
                AuthenticatedTokenResponse response = new AuthenticatedTokenResponse();
                response.setAuthenticatedToken(authenticatedToken);
                response.setExpire(validTime);
                response.setIssueAt(issueAt);
                return response;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate authentication link: " + e.getMessage(), e);
            }
        }).onErrorResume(throwable -> {
            return Mono.error(new RuntimeException("Authentication link generation failed", throwable));
        });
    }

    @Override
    public Mono<UserAuthTokenResponse> extendAuthenticationLink(ExtendAuthenTokenRequest request) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = jwtTokenProducer.extractClaims(request.getExistingToken());
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                String name = claims.get("name", String.class);
                if (role == null)
                    role = "SELLER";

                long validTime = request.getExtensionMillis() > 0
                        ? request.getExtensionMillis()
                        : 15 * 60 * 1000; // default 15 min

                String newToken = jwtTokenProducer.generateTokenByValidTime(username, role, validTime, name);

                Date issueAt = new Date();
                Date expire = new Date(issueAt.getTime() + validTime);

                UserAuthTokenResponse response = new UserAuthTokenResponse();
                response.setAuthenticationToken(newToken);
                response.setIssueAt(issueAt);
                response.setExpire(expire);
                return response;
            } catch (Exception e) {
                log.error("Failed to extend authentication link: {}", e.getMessage());
                throw new RuntimeException("Failed to extend authentication link", e);
            }
        }).onErrorResume(
                throwable -> Mono.error(new RuntimeException("Authentication link extension failed", throwable)));
    }
}
