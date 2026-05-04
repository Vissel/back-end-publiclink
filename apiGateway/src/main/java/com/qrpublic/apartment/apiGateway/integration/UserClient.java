package com.qrpublic.apartment.apiGateway.integration;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class UserClient {

    private final WebClient webClient;

    public UserClient(@Value("${user.service.url:http://localhost:8082}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    public Mono<FindUserResponse> findUserByUsername(FindUserRequest request) {
        return webClient.post()
                .uri("/api/v1/user/findByUsername")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FindUserResponse.class);
    }
}
