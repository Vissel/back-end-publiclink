package com.qrpublic.apartment.apiGateway.integration;

import com.qrpublic.apartment.adapter.authentication.response.TokenClaimsResponse;
import com.qrpublic.apartment.adapter.user.request.ExtendAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.request.UserAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import com.qrpublic.apartment.apiGateway.authentication.request.AuthenticatedTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.AuthenticatedTokenResponse;
import com.qrpublic.apartment.apiGateway.service.SecurityCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/security/v1/public")
public class SecurityCheckPublicController {
    @Autowired
    SecurityCheckService securityCheckService;

    @PostMapping("/check-token")
    public Mono<ResponseEntity<Boolean>> checkToken(@RequestBody String token) {
        return securityCheckService.checkValidToken(token)
                .map(valid -> ResponseEntity.ok(valid));
    }

    @PostMapping("/extract-token-claims")
    public Mono<TokenClaimsResponse> extractTokenClaims(@RequestBody String token) {
        return securityCheckService.extractTokenClaims(token);
    }

    @PostMapping("/generateAuthenToken")
    public Mono<UserAuthTokenResponse> generateAuthenToken(@RequestBody UserAuthenTokenRequest userAuthenTokenRequest) {
        AuthenticatedTokenRequest request = convertToAuthenticatedLinkRequest(userAuthenTokenRequest);
        return convertToUserAuthLinkResponse(securityCheckService.generateAuthenticationLink(request));
    }

    private Mono<UserAuthTokenResponse> convertToUserAuthLinkResponse(
            Mono<AuthenticatedTokenResponse> authenticatedLinkResponseMono) {
        return authenticatedLinkResponseMono.map(src -> {
            UserAuthTokenResponse res = new UserAuthTokenResponse();
            res.setAuthenticationToken(src.getAuthenticatedToken());
            res.setIssueAt(src.getIssueAt());
            res.setExpire(src.getExpire());
            return res;
        });
    }

    private AuthenticatedTokenRequest convertToAuthenticatedLinkRequest(UserAuthenTokenRequest userAuthenTokenRequest) {
        // Convert UserAuthenLinkRequest properties to AuthenticatedLinkRequest
        // properties
        AuthenticatedTokenRequest request = new AuthenticatedTokenRequest();
        request.setUsername(userAuthenTokenRequest.getUsername());
        request.setValidTime(userAuthenTokenRequest.getValidTime());
        request.setRole(userAuthenTokenRequest.getRole());
        request.setName(userAuthenTokenRequest.getName());
        return request;
    }

    @PostMapping("/extendAuthenToken")
    public Mono<UserAuthTokenResponse> extendAuthenToken(@RequestBody ExtendAuthenTokenRequest request) {
        return securityCheckService.extendAuthenticationLink(request);
    }

}
