package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.core.model.ChatStatusEnum;
import com.qrpublic.apartment.chat.core.model.ZaloCredentialModel;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import com.qrpublic.apartment.chat.core.service.CoreZaloService;
import com.qrpublic.apartment.chat.integration.ZaloOaClient;
import com.qrpublic.apartment.chat.integration.model.ZaloApiException;
import com.qrpublic.apartment.chat.integration.model.ZaloSendTextRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

/**
 * The Zalo leg of the bridge: pushes one chat message into the administrator's
 * Zalo thread and reports the acknowledgement as a message status.
 *
 * Outbound text carries the conversation tag (for example
 * {@code [Chat 0042] ...})
 * because every customer shares the single OA-to-administrator Zalo thread; the
 * tag
 * is what allows an unquoted reply to be routed back to the right visitor.
 */
@Slf4j
@Component
public class ZaloBridgeDomain {

    /** Reason code when no administrator Zalo account has been bound yet. */
    public static final String REASON_ADMIN_NOT_BOUND = "ADMIN_NOT_BOUND";

    @Autowired
    ZaloTokenDomain zaloTokenDomain;

    @Autowired
    ZaloOaClient zaloOaClient;

    @Autowired
    CoreZaloService coreZaloService;

    @Autowired
    CoreChatService coreChatService;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    /** Set this once the bound-admin flow is used instead of the configured id. */
    @Value("${zalo.admin-user-id:}")
    private String configuredAdminUserId;

    @Value("${zalo.dispatch-timeout-millis:8000}")
    private long dispatchTimeoutMillis;

    /** Zalo refuses consultative text above 2000 characters. */
    @Value("${zalo.max-text-length:2000}")
    private int maxTextLength;

    public boolean isBridgeEnabled() {
        return zaloTokenDomain.isEnabled();
    }

    /**
     * Sends one client message to the administrator and records the outcome.
     * Never throws: a Zalo refusal leaves the message FAILED with the Zalo code
     * stored, and the chat itself keeps working.
     */
    public Mono<ZaloSendOutcome> dispatchClientMessage(ChatMessageModel message) {
        String text = buildText(message.getClientTag(), message.getContent());
        return sendToAdmin(text, message.getZaloMessageId())
                .flatMap(outcome -> applyOutcome(message.getId(), outcome));
    }

    /**
     * Mirrors an administrator answer written in the console into the Zalo thread.
     */
    public Mono<ZaloSendOutcome> dispatchAdminReply(String clientTag, String content, String quoteZaloMsgId) {
        return sendToAdmin(buildText(clientTag, content), quoteZaloMsgId);
    }

    private Mono<ZaloSendOutcome> sendToAdmin(String text, String quoteZaloMsgId) {
        return resolveAdminUserId()
                .flatMap(adminUserId -> zaloTokenDomain.getAccessToken()
                        .flatMap(token -> zaloOaClient.sendText(token,
                                ZaloSendTextRequest.of(adminUserId, text, quoteZaloMsgId)))
                        .map(response -> ZaloSendOutcome.delivered(response.getData().getMessageId())))
                .timeout(Duration.ofMillis(dispatchTimeoutMillis))
                .onErrorResume(ZaloApiException.class, error -> {
                    log.warn("Zalo dispatch refused: {}", error.getMessage());
                    return Mono.just(ZaloSendOutcome.failed(error.getZaloCode(), error.getMessage()));
                })
                .onErrorResume(error -> {
                    log.warn("Zalo dispatch failed ({}): {}", error.getClass().getSimpleName(), error.getMessage());
                    return Mono.just(ZaloSendOutcome.failed(null, rootMessage(error)));
                });
    }

    private Mono<String> resolveAdminUserId() {
        if (configuredAdminUserId != null && !configuredAdminUserId.isBlank()) {
            return Mono.just(configuredAdminUserId.trim());
        }
        return Mono.fromCallable(() -> coreZaloService.findCredential(CoreZaloService.SCOPE_ADMIN_USER)
                .map(ZaloCredentialModel::getValueA)
                .filter(value -> value != null && !value.isBlank()))
                .subscribeOn(Schedulers.boundedElastic())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        REASON_ADMIN_NOT_BOUND + ": no administrator Zalo account bound yet")));
    }

    private Mono<ZaloSendOutcome> applyOutcome(Long messageId, ZaloSendOutcome outcome) {
        ChatStatusEnum target = outcome.isDelivered() ? ChatStatusEnum.SENT : ChatStatusEnum.FAILED;
        return Mono.fromCallable(() -> coreChatService.advanceStatus(messageId, target, outcome.getZaloMessageId(),
                outcome.isDelivered() ? null : outcome.getErrorCode()))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(updated -> updated.ifPresent(chatEventPublisher::publishStatus))
                .thenReturn(outcome)
                .doOnError(error -> log.error("Unable to record Zalo outcome for message {}: {}",
                        messageId, error.getMessage()))
                .onErrorResume(error -> Mono.just(outcome));
    }

    private String buildText(String clientTag, String content) {
        String body = content == null ? "" : content;
        if (clientTag == null || clientTag.isBlank()) {
            return clamp(body);
        }
        String header = "[Chat " + clientTag + "] ";
        int room = Math.max(0, maxTextLength - header.length());
        return header + clamp(body, room);
    }

    private String clamp(String value) {
        return clamp(value, maxTextLength);
    }

    private static String clamp(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }
}
