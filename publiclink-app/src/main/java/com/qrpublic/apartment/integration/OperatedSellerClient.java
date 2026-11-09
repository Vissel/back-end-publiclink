package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.authentication.request.FindUserAuthenRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserAuthenResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserUserAuthRequest;
import com.qrpublic.apartment.adapter.user.response.UserUserAuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OperatedSellerClient {

    private final WebClient webClient;

    public OperatedSellerClient(@Value("${user.service.url:http://localhost:8082}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl + "/api/v1/seller").build();
    }

    public Mono<FindUserAuthenResponse> findUserByUsername(FindUserAuthenRequest request) {
        return webClient.post()
                .uri("/findByUsername")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FindUserAuthenResponse.class);
    }

    public Mono<Result<UserUserAuthResponse>> updateUserAuth(UserUserAuthRequest request) {
        return webClient.post()
                .uri("/updateUserAndUserAuth")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<UserUserAuthResponse>>() {
                });
    }

    public Mono<Result<UserUserAuthResponse>> invalidateUserAuth(FindUserAuthenRequest request) {
        return webClient.post()
                .uri("/invalidateUserAuth")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<UserUserAuthResponse>>() {
                });
    }
}
