package com.qrpublic.apartment.apiGateway.service.impl;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.authentication.request.AuthenticatedTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.AuthenticatedTokenResponse;
import com.qrpublic.apartment.apiGateway.service.SecurityCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;

@Service
public class SecurityCheckServiceImpl implements SecurityCheckService {

    @Autowired
    JwtTokenProducer jwtTokenProducer;

    @Override
    public Mono<Boolean> checkValidToken(String token) {
        return jwtTokenProducer.validateToken(token);
    }

    @Override
    public Mono<AuthenticatedTokenResponse> generateAuthenticationLink(AuthenticatedTokenRequest authenticatedTokenRequest) {
        return Mono.fromCallable(() -> {
            try {
                String authenticatedToken = jwtTokenProducer.generateTokenByValidTime(authenticatedTokenRequest.getUsername(),
                        authenticatedTokenRequest.getRole(), authenticatedTokenRequest.getValidTime());
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
}
