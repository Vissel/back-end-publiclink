package com.qrpublic.apartment.chat.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the human name of a signed-in chat participant from the user
 * service, so the administrator console shows "Nguyen Van A" instead of a
 * device tag.
 *
 * Purely cosmetic and deliberately best effort: the authoritative identity is
 * the username the gateway forwards, and a user service that is slow or down
 * must never stop a visitor from chatting. The lookup therefore has a short
 * timeout and falls back to the username.
 */
@Slf4j
@Component
public class ChatAccountDomain {

    private final WebClient webClient;

    public ChatAccountDomain(@Value("${chat.user-service-url:http://127.0.0.1:8082}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    /**
     * Called from the service layer's bounded-elastic worker, never from the
     * event loop, so the bounded block below is safe.
     */
    public Optional<String> displayName(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            FoundUser found = webClient.post()
                    .uri("/api/v1/user/findByUsername")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("userName", username))
                    .retrieve()
                    .bodyToMono(FoundUser.class)
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(error -> {
                        log.debug("Chat display-name lookup failed for {}: {}", username, error.getMessage());
                        return Mono.empty();
                    })
                    .block(Duration.ofSeconds(4));
            if (found == null || found.name() == null || found.name().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(found.name().trim());
        } catch (Exception e) {
            log.debug("Chat display-name lookup unavailable for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    /** Subset of the user service's FindUserResponse that the console needs. */
    private record FoundUser(String userName, String name, String role) {
    }
}
