package com.qrpublic.apartment.apiGateway.authentication;

import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.RefreshTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.RefreshTokenResponse;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Mono;

import java.io.IOException;

public interface AuthenticationService {
    ByteArrayResource getPublicKey() throws IOException;

    Mono<BasicLoginResponse> basicLogin(BasicLoginRequest request);

    Mono<RefreshTokenResponse> refreshAccessToken(RefreshTokenRequest request);

    Mono<Boolean> logout(String refreshToken);
}
