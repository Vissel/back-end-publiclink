package com.qrpublic.apartment.chat.controller;

import com.qrpublic.apartment.chat.request.ChatSystemMessageRequest;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.service.ChatService;
import com.qrpublic.apartment.chat.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Server-to-server chat writes. The gateway explicitly denies this namespace;
 * publiclink-app reaches chat-app directly on the internal service port.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/chat")
public class ChatInternalController {

    @Autowired
    ChatService chatService;

    @PostMapping("/system-message")
    public Mono<ResponseEntity<ChatMessageResponse>> systemMessage(
            @RequestBody ChatSystemMessageRequest request) {
        return Mono.fromCallable(() -> chatService.systemMessage(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
