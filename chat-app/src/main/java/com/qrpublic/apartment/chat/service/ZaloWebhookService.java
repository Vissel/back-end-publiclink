package com.qrpublic.apartment.chat.service;

import com.qrpublic.apartment.chat.domain.ZaloEventDomain;
import com.qrpublic.apartment.chat.template.model.Result;
import com.qrpublic.apartment.chat.template.service.ProcessCallback;
import com.qrpublic.apartment.chat.template.service.ChatServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service layer in front of the Zalo webhook. Kept deliberately thin: Zalo
 * expects
 * HTTP 200 within two seconds and disables a webhook that keeps missing it, so
 * the
 * only synchronous work is authentication. Actual processing is handed to a
 * worker
 * thread and survives failures through the persisted event rows.
 */
@Slf4j
@Service
public class ZaloWebhookService {

    @Autowired
    ChatServiceTemplate chatServiceTemplate;

    @Autowired
    ZaloEventDomain zaloEventDomain;

    /** Signature check, run before anything else and before any database write. */
    public Result<Boolean> isAuthentic(String rawBody, String signatureHeader) {
        return chatServiceTemplate.execute(new ProcessCallback<Void, Boolean>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // no validation: an absent body simply fails the signature comparison below
            }

            @Override
            public Boolean process() {
                return zaloEventDomain.isAuthentic(rawBody, signatureHeader);
            }
        });
    }

    /**
     * Acknowledges the delivery and processes it off the request thread. Duplicate
     * retries from Zalo are recognised inside the domain and skipped.
     */
    public Result<Void> acceptAsync(String rawBody) {
        return chatServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // body may legitimately be an empty challenge delivery
            }

            @Override
            public Void process() {
                Mono.fromRunnable(() -> zaloEventDomain.accept(rawBody))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(error -> log.error("Zalo event processing failed: {}", error.getMessage()))
                        .onErrorResume(error -> Mono.empty())
                        .subscribe();
                return null;
            }
        });
    }
}
