package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.adapter.user.response.UserRemoveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OperatedUserClient {

    private final WebClient webClient;

    public OperatedUserClient(@Value("${user.service.url:http://localhost:8082}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    public Mono<FindUserResponse> findUserByUsername(FindUserRequest request) {
        return webClient.post()
                .uri("/api/v1/user/findByUsername")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FindUserResponse.class);
    }

    public Mono<Result<UserRegisterResponse>> createUser(UserRegisterRequest request) {
        return webClient.post()
                .uri("/api/v1/user/createUser")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<UserRegisterResponse>>() {});
    }

    public Mono<Result<UserRemoveResponse>> deleteUser(UserRemoveRequest request) {
        return webClient.post()
                .uri("/api/v1/user/deleteUser")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<UserRemoveResponse>>() {});
    }
}
