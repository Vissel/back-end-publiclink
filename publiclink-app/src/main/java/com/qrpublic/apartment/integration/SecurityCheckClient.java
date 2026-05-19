package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.authentication.response.TokenClaimsResponse;
import com.qrpublic.apartment.adapter.user.request.UserAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SecurityCheckClient {

    private final WebClient webClient;

    public SecurityCheckClient(@Value("${security.service.url:http://localhost:8080}") String securityServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(securityServiceUrl).build();
    }

    public Mono<Boolean> checkToken(String token) {
        return webClient.post()
                .uri("/security/v1/public/check-token")
                .bodyValue(token)
                .retrieve()
                .bodyToMono(Boolean.class);
    }

    public Mono<TokenClaimsResponse> extractTokenClaims(String token) {
        return webClient.post()
                .uri("/security/v1/public/extract-token-claims")
                .bodyValue(token)
                .retrieve()
                .bodyToMono(TokenClaimsResponse.class);
    }

    public Mono<UserAuthTokenResponse> generateAuthenToken(UserAuthenTokenRequest request) {
        return webClient.post()
                .uri("/security/v1/public/generateAuthenToken")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserAuthTokenResponse.class);
    }

}
