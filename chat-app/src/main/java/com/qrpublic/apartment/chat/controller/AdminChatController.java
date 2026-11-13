package com.qrpublic.apartment.chat.controller;

import com.qrpublic.apartment.chat.request.AdminChatReplyRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.request.PushSubscriptionRequest;
import com.qrpublic.apartment.chat.response.AdminConversationListResponse;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.response.StreamTicketResponse;
import com.qrpublic.apartment.chat.response.ZaloConsentResponse;
import com.qrpublic.apartment.chat.response.VapidPublicKeyResponse;
import com.qrpublic.apartment.chat.service.AdminChatService;
import com.qrpublic.apartment.chat.service.ZaloConnectService;
import com.qrpublic.apartment.chat.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
     * Single-use credential for the event stream. This call carries the JWT, the
     * stream cannot - EventSource has no header hook - so the console asks here
     * first and puts the returned ticket in the stream URL.
     */
    @PostMapping("/stream-ticket")
    public Mono<ResponseEntity<StreamTicketResponse>> streamTicket() {
        return Mono.fromCallable(() -> adminChatService.issueStreamTicket())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Live inbox feed: messages, status transitions, presence and visitor typing.
     *
     * The gateway lets this one path through without a JWT, which makes the
     * ticket the authentication of this endpoint: single-use, sixty seconds.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> stream(
            @RequestParam(required = false) String ticket) {
        if (!adminChatService.authorizeStream(ticket)) {
            return Mono.just(ResponseEntity.<Flux<ServerSentEvent<String>>>status(HttpStatus.UNAUTHORIZED).build());
        }
        return Mono.just(ResponseEntity.ok(adminChatService.stream()));
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

    // ------------------------------------------------ background notifications

    /**
     * Stores this browser's push subscription. The administrator is taken from
     * the gateway-forwarded identity header, so the browser can only subscribe
     * the account it is signed in with.
     */
    @PostMapping("/push/subscribe")
    public Mono<ResponseEntity<Void>> subscribePush(
            @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String adminUsername) {
        return Mono.fromCallable(() -> adminChatService.subscribePush(request, adminUsername))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** Called when the switch is turned off or the permission is revoked. */
    @PostMapping("/push/unsubscribe")
    public Mono<ResponseEntity<Void>> unsubscribePush(@RequestBody PushSubscriptionRequest request) {
        return Mono.fromCallable(() -> adminChatService.unsubscribePush(
                request == null ? null : request.getEndpoint()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * The VAPID key the service worker registration needs, if push is configured.
     */
    @GetMapping("/push/vapid-public-key")
    public Mono<ResponseEntity<VapidPublicKeyResponse>> vapidPublicKey() {
        return Mono.fromCallable(() -> adminChatService.vapidPublicKey())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
