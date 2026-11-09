package com.qrpublic.apartment.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zalo chat bridge service.
 *
 * Runs the client-side app chat and the Zalo leg in one process, separate from
 * publiclink-app on purpose: the visitor event streams, the in-memory stream
 * registry and the single-flight Zalo token refresh all live in process memory,
 * so releasing the core business service no longer drops every open chat.
 *
 * @EnableScheduling is required — token refresh, webhook event re-drive and the
 * idle-conversation sweep are scheduled.
 */
@SpringBootApplication
@EnableScheduling
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
