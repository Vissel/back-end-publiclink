package com.qrpublic.apartment.apiGateway.service;

import com.qrpublic.apartment.adapter.authentication.response.TokenClaimsResponse;
import com.qrpublic.apartment.adapter.user.request.ExtendAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import com.qrpublic.apartment.apiGateway.authentication.request.AuthenticatedTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.AuthenticatedTokenResponse;
import reactor.core.publisher.Mono;

public interface SecurityCheckService {
    Mono<Boolean> checkValidToken(String token);

    Mono<AuthenticatedTokenResponse> generateAuthenticationLink(AuthenticatedTokenRequest authenticatedTokenRequest);

    Mono<TokenClaimsResponse> extractTokenClaims(String token);

    Mono<UserAuthTokenResponse> extendAuthenticationLink(ExtendAuthenTokenRequest request);

}
