package com.qrpublic.apartment.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Best-effort server-to-server delivery of booking updates to chat-app. */
@Slf4j
@Component
public class ChatMessageClient {
    private final WebClient webClient;

    public ChatMessageClient(@Value("${chat.service.url:http://localhost:9090}") String chatServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(chatServiceUrl).build();
    }

    public Mono<Void> sendSystemMessage(String ownerUsername, String content) {
        if (ownerUsername == null || ownerUsername.isBlank() || content == null || content.isBlank()) {
            return Mono.empty();
        }
        return webClient.post()
                .uri("/chat/internal/v1/chat/system-message")
                .bodyValue(new SystemMessageRequest(ownerUsername, content))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(error -> {
                    log.warn("Chat system-message delivery failed for [{}]: {}", ownerUsername, error.getMessage());
                    return Mono.empty();
                });
    }

    /** Exact chat-app request body. */
    public record SystemMessageRequest(String ownerUsername, String content) {
    }
}
