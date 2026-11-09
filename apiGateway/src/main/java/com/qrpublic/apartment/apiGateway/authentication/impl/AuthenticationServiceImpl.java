package com.qrpublic.apartment.apiGateway.authentication.impl;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.user.request.UserChangePasswordRequest;
import com.qrpublic.apartment.apiGateway.authentication.AuthenticationService;
import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.authentication.RefreshTokenService;
import com.qrpublic.apartment.apiGateway.authentication.RefreshTokenStorageService;
import com.qrpublic.apartment.apiGateway.authentication.RsaService;
import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.ChangePasswordRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.RefreshTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.ChangePasswordResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.RefreshTokenResponse;
import com.qrpublic.apartment.apiGateway.exception.BusinessException;
import com.qrpublic.apartment.apiGateway.exception.ErrorCode;
import com.qrpublic.apartment.apiGateway.integration.UserClient;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final long PASSWORD_CHANGE_TICKET_TTL_SECONDS = 300;

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

    @Autowired
    RefreshTokenStorageService storageService;

    @Autowired
    UserClient userClient;

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
            return Mono.error(new BusinessException(ErrorCode.INVALID_ARGUMENTS, new Object[] { errorMessage }));
        }

        String reqUuid = request.getReqUuid();

        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(),
                        rsaService.decrypt(request.getEncryptedPassword())))
                .flatMap(authentication -> userClient
                        .findUserByUsername(new FindUserRequest(authentication.getName()))
                        .defaultIfEmpty(new FindUserResponse())
                        .flatMap(user -> {
                            if (Boolean.TRUE.equals(user.getMustChangePassword())) {
                                return handleMustChangePassword(authentication.getName(), reqUuid);
                            }
                            return generateTokenAndResponse(authentication, reqUuid, user.getName());
                        }))
                .onErrorMap(this::handleAuthenticationFailure);

    }

    /**
     * First-login flow: the password is valid but still the generated one.
     * Issue NO tokens; hand out a single-use ticket that only unlocks the
     * change-password endpoint.
     */
    private Mono<BasicLoginResponse> handleMustChangePassword(String username, String reqUuid) {
        String ticket = UUID.randomUUID().toString();

        return storageService.storePasswordChangeTicket(ticket, username, PASSWORD_CHANGE_TICKET_TTL_SECONDS)
                .flatMap(stored -> {
                    if (!stored) {
                        log.error("Failed to store password-change ticket for user: {}", username);
                        return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_FAILED));
                    }

                    BasicLoginResponse.BasicLoginResponseBuilder builder = BasicLoginResponse.builder()
                            .authenticated(false)
                            .changePasswordRequired(true)
                            .passwordChangeTicket(ticket)
                            .username(username)
                            .message("Password change required before first login.");

                    if (reqUuid != null && !reqUuid.isBlank()) {
                        builder.reqUuid(reqUuid);
                    }

                    return Mono.just(builder.build());
                });
    }

    private Mono<BasicLoginResponse> generateTokenAndResponse(Authentication authentication, String reqUuid,
            String name) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                .toList();

        String accessToken = tokenProvider.generateToken(username, roles, name);
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
        if (error instanceof BusinessException)
            return error;
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

    @Override
    public Mono<ChangePasswordResponse> changePassword(ChangePasswordRequest request) {
        // Validate the request in WebFlux context
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((s1, s2) -> s1 + ", " + s2)
                    .orElse("Validation failed");
            return Mono.error(new BusinessException(ErrorCode.INVALID_ARGUMENTS, new Object[] { errorMessage }));
        }

        // Atomic single-use ticket consumption; empty means invalid/expired
        return storageService.consumePasswordChangeTicket(request.getTicket())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PASSWORD_CHANGE_TICKET_INVALID)))
                .flatMap(username -> {
                    UserChangePasswordRequest changeRequest = new UserChangePasswordRequest();
                    changeRequest.setUserName(username);
                    changeRequest.setEncryptedPassword(request.getEncryptedPassword());
                    return userClient.changePassword(changeRequest);
                })
                .map(resp -> ChangePasswordResponse.builder()
                        .changed(Boolean.TRUE.equals(resp.getChanged()))
                        .message(resp.getMessage() != null ? resp.getMessage() : "Change password successfully")
                        .build())
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("User service rejected password change: status {} | {}", e.getStatusCode(),
                            e.getMessage());
                    return Mono.error(new BusinessException(ErrorCode.INVALID_ARGUMENTS,
                            new Object[] { "Invalid new password" }));
                });
    }
}
