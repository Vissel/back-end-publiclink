package com.qrpublic.apartment.chat.scheduler;

import com.qrpublic.apartment.chat.domain.ChatEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the administrator console event stream alive.
 *
 * SSE frames only flow when something happens, and a quiet inbox means a quiet
 * connection - long enough and nginx or the tunnel closes it, after which the
 * console looks connected but never learns about a new guest message. A ping
 * every 25 seconds is below every relevant idle timeout in the chain and costs
 * a handful of bytes.
 */
@Slf4j
@Component
public class ChatStreamKeepAlive {

    @Autowired
    ChatEventPublisher chatEventPublisher;

    @Scheduled(fixedDelayString = "${chat.stream-ping-ms:25000}")
    public void pingAdmins() {
        try {
            chatEventPublisher.publishAdminPing();
        } catch (Exception e) {
            // A failed keepalive is never worth a scheduler thread; the console
            // reconnects on its own if the stream does actually drop.
            log.warn("Chat admin keepalive ping failed: {}", e.getMessage());
        }
    }
}
