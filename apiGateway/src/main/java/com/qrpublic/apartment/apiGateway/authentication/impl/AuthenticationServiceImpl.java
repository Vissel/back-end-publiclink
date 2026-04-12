package com.qrpublic.apartment.apiGateway.authentication.impl;

import com.qrpublic.apartment.apiGateway.authentication.AuthenticationService;
import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.authentication.RsaService;
import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import com.qrpublic.apartment.apiGateway.exception.BusinessException;
import com.qrpublic.apartment.apiGateway.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    @Autowired
    ReactiveAuthenticationManager authenticationManager;

    @Autowired
    RsaService rsaService;

    @Autowired
    JwtTokenProducer tokenProvider;

    @Override
    public ByteArrayResource getPublicKey() throws IOException {
        return new ByteArrayResource(rsaService.loadPublicKey());
    }

    @Override
    public Mono<BasicLoginResponse> basicLogin(BasicLoginRequest request) {
        return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), rsaService.decrypt(request.getEncryptedPassword()))).flatMap(this::generateTokenAndResponse).onErrorMap(this::handleAuthenticationFailure);
    }

    private Mono<BasicLoginResponse> generateTokenAndResponse(Authentication authentication) {
        String username = authentication.getName();
        var roles = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();

        String accessToken = tokenProvider.generateToken(username, roles);
        String refreshToken = tokenProvider.generateRefreshToken(username);

        return Mono.just(BasicLoginResponse.builder().token(accessToken).refreshToken(refreshToken)
//                .tokenType("Bearer")
//                .expiresIn(3600L)
                .username(username).authenticated(true)
//                .roles(roles)
                .build());
    }

    private Throwable handleAuthenticationFailure(Throwable error) {
        log.error("Authentication failed", error);
        if (error instanceof BusinessException) return error;
        return new BusinessException(ErrorCode.AUTHENTICATION_FAILED, error);
    }
}
