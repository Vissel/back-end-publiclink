package com.qrpublic.apartment.chat.controller;

import com.qrpublic.apartment.chat.request.ChatHistoryRequest;
import com.qrpublic.apartment.chat.request.ChatSendMessageRequest;
import com.qrpublic.apartment.chat.request.ChatStartRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHealthResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.response.ChatStartResponse;
import com.qrpublic.apartment.chat.service.ChatService;
import com.qrpublic.apartment.chat.service.ZaloConnectService;
import com.qrpublic.apartment.chat.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Public entry point of the client side of the chat bridge.
 *
 * Paths are relative to the configured webflux base path, so the outside world
 * reaches them as {@code /publiclink/api/v1/chat/**}. They are anonymous by
 * design:
 * the visitor is recognised by device identity rather than by an account, which
 * is
 * why they are listed in the gateway permit-all set.
 *
 * Client IP is taken from the {@code X-Real-IP} header set by nginx. The socket
 * address cannot be used: nginx, gateway and app share one host, so every
 * request
 * arrives from 127.0.0.1.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    @Autowired
    ChatService chatService;

    @Autowired
    ZaloConnectService zaloConnectService;

    @Value("${chat.public-console-url:/adminChat}")
    private String consoleRedirectUrl;

    /**
     * Liveness probe. This is what a deployment verifies: a 200 here proves nginx,
     * the gateway /chat/** route and chat-app are all alive at once.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<ChatHealthResponse>> health() {
        return Mono.fromCallable(() -> chatService.health())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/start")
    public Mono<ResponseEntity<ChatStartResponse>> start(
            @RequestBody ChatStartRequest request,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        // The template runs blocking JPA, so the whole call is moved off the event
        // loop.
        return Mono.fromCallable(() -> chatService.start(request, resolveIp(realIp, forwardedFor), userAgent))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/message")
    public Mono<ResponseEntity<ChatMessageResponse>> message(@RequestBody ChatSendMessageRequest request) {
        return Mono.fromCallable(() -> chatService.sendMessage(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/history")
    public Mono<ResponseEntity<ChatHistoryResponse>> history(@RequestBody ChatHistoryRequest request) {
        return Mono.fromCallable(() -> chatService.history(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Fires the visitor's typing indicator; never fails the chat when it misfires.
     */
    @PostMapping("/typing")
    public Mono<ResponseEntity<Void>> typing(@RequestBody ChatTypingRequest request) {
        return Mono.fromCallable(() -> chatService.typing(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/close")
    public Mono<ResponseEntity<ChatConversationResponse>> close(@RequestParam String conversationUuid) {
        return Mono.fromCallable(() -> chatService.closeConversation(conversationUuid))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Live status/message feed for one visitor. Reconnects are expected and cheap:
     * the browser sends the last event id it saw and the server replays the gap.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String conversationUuid,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return chatService.attachStream(deviceId, conversationUuid, parseLong(lastEventId));
    }

    /**
     * Zalo OAuth landing for the OA administrator consent. It has to be public
     * because Zalo redirects the administrator's browser here without our JWT; the
     * single-use random {@code state} held server-side is what binds the callback
     * to
     * the console that started it.
     */
    @GetMapping("/zalo/consent/callback")
    public Mono<ResponseEntity<Void>> consentCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        return zaloConnectService.completeConsent(code, state)
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", consoleRedirectUrl + "?zalo=connected")
                        .<Void>build())
                .onErrorResume(error -> {
                    log.error("Zalo consent callback failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                            .header("Location", consoleRedirectUrl + "?zalo=failed")
                            .<Void>build());
                });
    }

    private static String resolveIp(String realIp, String forwardedFor) {
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
