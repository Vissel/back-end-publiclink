package com.qrpublic.apartment.chat.controller;

import com.qrpublic.apartment.chat.request.AdminChatReplyRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.response.AdminConversationListResponse;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.response.ZaloConsentResponse;
import com.qrpublic.apartment.chat.service.AdminChatService;
import com.qrpublic.apartment.chat.service.ZaloConnectService;
import com.qrpublic.apartment.chat.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Administrator console of the chat bridge.
 *
 * Not in the gateway permit-all list, so reaching these routes already requires
 * a
 * valid JWT issued by the gateway — consistent with how AdminController is
 * guarded.
 * The console is optional: an administrator who answers in the Zalo app never
 * needs
 * it, and both paths write into the same conversation.
 */
@Slf4j
@RestController
@RequestMapping("/admin/v1/chat")
public class AdminChatController {

    @Autowired
    AdminChatService adminChatService;

    @Autowired
    ZaloConnectService zaloConnectService;

    @GetMapping("/conversations")
    public Mono<ResponseEntity<AdminConversationListResponse>> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Mono.fromCallable(() -> adminChatService.listConversations(page, size))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @GetMapping("/history")
    public Mono<ResponseEntity<ChatHistoryResponse>> history(@RequestParam String conversationUuid) {
        return Mono.fromCallable(() -> adminChatService.history(conversationUuid))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/reply")
    public Mono<ResponseEntity<ChatMessageResponse>> reply(@RequestBody AdminChatReplyRequest request) {
        return Mono.fromCallable(() -> adminChatService.reply(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Broadcasts "administrator is typing…" to the visitor — only possible from
     * here.
     */
    @PostMapping("/typing")
    public Mono<ResponseEntity<Void>> typing(@RequestBody ChatTypingRequest request) {
        return Mono.fromCallable(() -> adminChatService.typing(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/close")
    public Mono<ResponseEntity<ChatConversationResponse>> close(@RequestParam String conversationUuid) {
        return Mono.fromCallable(() -> adminChatService.close(conversationUuid))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Live inbox feed: messages, status transitions, presence and visitor typing.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        return adminChatService.stream();
    }

    @GetMapping("/zalo/status")
    public Mono<ResponseEntity<Map<String, Object>>> zaloStatus() {
        return Mono.fromCallable(() -> zaloConnectService.status())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** Starts the one-time Zalo OA consent for this application. */
    @PostMapping("/zalo/consent/begin")
    public Mono<ResponseEntity<ZaloConsentResponse>> beginZaloConsent() {
        return Mono.fromCallable(() -> zaloConnectService.beginConsent())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
