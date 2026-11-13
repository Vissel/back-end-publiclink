package com.qrpublic.apartment.chat.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * Regression test for the live-feed leak: the fan-out used to be keyed by device
 * id, which every account on one browser shares, so two users joined one sink
 * and read each other's messages. Keyed by conversation, a frame for one thread
 * must never reach another thread's subscriber.
 */
class ChatEventStreamRegistryTest {

    @Test
    void framesForOneConversationNeverReachAnother() {
        ChatEventStreamRegistry registry = new ChatEventStreamRegistry();

        Flux<ServerSentEvent<String>> micro08 = registry.clientStream("conv-08");
        Flux<ServerSentEvent<String>> micro09 = registry.clientStream("conv-09");

        StepVerifier.create(micro09.take(1).timeout(Duration.ofSeconds(2)))
                .then(() -> registry.publishToClient("conv-08",
                        ServerSentEvent.builder("secret-of-08").event("message").build()))
                .expectNoEvent(Duration.ofMillis(300))
                .then(() -> registry.publishToClient("conv-09",
                        ServerSentEvent.builder("hello-09").event("message").build()))
                .assertNext(event -> org.assertj.core.api.Assertions.assertThat(event.data()).isEqualTo("hello-09"))
                .verifyComplete();

        StepVerifier.create(micro08.take(1))
                .assertNext(event -> org.assertj.core.api.Assertions.assertThat(event.data()).isEqualTo("secret-of-08"))
                .verifyComplete();
    }

    @Test
    void onlineStateIsPerConversation() {
        ChatEventStreamRegistry registry = new ChatEventStreamRegistry();

        Flux<ServerSentEvent<String>> stream = registry.clientStream("conv-08");
        StepVerifier.create(stream.take(1))
                .then(() -> {
                    org.assertj.core.api.Assertions.assertThat(registry.isClientOnline("conv-08")).isTrue();
                    org.assertj.core.api.Assertions.assertThat(registry.isClientOnline("conv-09")).isFalse();
                    registry.publishToClient("conv-08", ServerSentEvent.builder("x").event("ping").build());
                })
                .expectNextCount(1)
                .verifyComplete();
    }
}
