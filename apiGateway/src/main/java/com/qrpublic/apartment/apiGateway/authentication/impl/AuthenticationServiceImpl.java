package com.qrpublic.apartment.apiGateway.authentication.impl;

import com.qrpublic.apartment.apiGateway.authentication.AuthenticationService;
import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.authentication.RefreshTokenService;
import com.qrpublic.apartment.apiGateway.authentication.RsaService;
import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.RefreshTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.RefreshTokenResponse;
import com.qrpublic.apartment.apiGateway.exception.BusinessException;
import com.qrpublic.apartment.apiGateway.exception.ErrorCode;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    @Autowired
    ReactiveAuthenticationManager authenticationManager;

    @Autowired
    RsaService rsaService;

    @Autowired
    JwtTokenProducer tokenProvider;

    @Autowired
    Validator validator;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Override
    public ByteArrayResource getPublicKey() throws IOException {
        return new ByteArrayResource(rsaService.loadPublicKey());
    }

    @Override
    public Mono<BasicLoginResponse> basicLogin(BasicLoginRequest request) {
        // Validate the request in WebFlux context
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((s1, s2) -> s1 + ", " + s2)
                    .orElse("Validation failed");
            return Mono.error(new BusinessException(ErrorCode.INVALID_ARGUMENTS, new Object[]{errorMessage}));
        }

        String reqUuid = request.getReqUuid();

        return
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getUsername(),
                                        rsaService.decrypt(request.getEncryptedPassword())))
                        .flatMap(authentication -> generateTokenAndResponse(authentication, reqUuid))
                        .onErrorMap(this::handleAuthenticationFailure);

    }

    private Mono<BasicLoginResponse> generateTokenAndResponse(Authentication authentication, String reqUuid) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();

        String accessToken = tokenProvider.generateToken(username, roles);
        String refreshToken = tokenProvider.generateRefreshToken(username);

        // Store refresh token in Redis for banking-grade security
        return refreshTokenService.storeInitialRefreshToken(refreshToken, username, roles)
                .map(stored -> {
                    BasicLoginResponse.BasicLoginResponseBuilder builder = BasicLoginResponse.builder()
                            .token(accessToken)
                            .refreshToken(refreshToken)
                            .username(username)
                            .authenticated(true)
                            .roles(roles)
                            .message("Login successful with generated token.");

                    if (reqUuid != null && !reqUuid.isBlank()) {
                        builder.reqUuid(reqUuid);
                    }

                    return builder.build();
                })
                .onErrorResume(e -> {
                    log.error("Failed to store refresh token for user: {}", username);
                    return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_FAILED));
                });
    }

    private Throwable handleAuthenticationFailure(Throwable error) {
        log.error("Authentication failed", error);
        if (error instanceof BusinessException) return error;
        return new BusinessException(ErrorCode.AUTHENTICATION_FAILED, error);
    }

    @Override
    public Mono<RefreshTokenResponse> refreshAccessToken(RefreshTokenRequest request) {
        return refreshTokenService.refreshAccessToken(request);
    }

    @Override
    public Mono<Boolean> logout(String refreshToken) {
        return refreshTokenService.logout(refreshToken);
    }
}
