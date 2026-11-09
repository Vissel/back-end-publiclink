package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.service.ZaloWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Inbound Zalo webhook — the only way the bridge learns that the administrator
 * answered, or that a message reached and was opened on their Zalo device.
 *
 * Two hard rules from the platform are respected here:
 * - answer HTTP 200 within two seconds, otherwise Zalo retries (30s, 5m, 15m, 30m,
 *   1h) and finally disables the webhook;
 * - authenticate every delivery with the OA secret key before touching the database,
 *   because this endpoint is publicly reachable and carries no JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/zalo")
public class ZaloWebhookController {

    private static final String SIGNATURE_HEADER = "X-ZEvent-Signature";

    @Autowired
    ZaloWebhookService zaloWebhookService;

    /** Contents served back to Zalo for the domain-ownership check. */
    @Value("${zalo.domain-verification-code:}")
    private String domainVerificationCode;

    @PostMapping("/webhook")
    public Mono<ResponseEntity<String>> webhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = "num_retry", required = false) String retryCount) {

        String body = rawBody == null ? "" : rawBody;
        if (retryCount != null && !retryCount.isBlank()) {
            log.warn("Zalo is retrying this webhook delivery (attempt {})", retryCount);
        }

        return Mono.fromCallable(() -> zaloWebhookService.isAuthentic(body, signature))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(verified -> {
                    if (!isTrusted(verified)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature"));
                    }
                    return Mono.fromCallable(() -> zaloWebhookService.acceptAsync(body))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(acknowledged -> ResponseEntity.ok("ok"));
                })
                .onErrorResume(error -> {
                    // Never let a processing fault cost us the acknowledgement: a
                    // non-200 makes Zalo retry and can disable the webhook outright.
                    log.error("Zalo webhook handling threw, acking anyway: {}", error.getMessage());
                    return Mono.just(ResponseEntity.ok("ok"));
                });
    }

    /**
     * Zalo ownership verification file, e.g. {@code /zalo/zalo_verifier1234.html}.
     * Register the URL prefix in the Zalo app, then confirm ownership there.
     */
    @GetMapping("/{fileName:.+}")
    public Mono<ResponseEntity<String>> verifier(@PathVariable String fileName) {
        return Mono.fromSupplier(() -> {
            if (fileName.startsWith("zalo_verifier") && !domainVerificationCode.isBlank()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(domainVerificationCode);
            }
            return ResponseEntity.notFound().<String>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean isTrusted(Result<Boolean> verified) {
        return verified != null && verified.isSuccess() && Boolean.TRUE.equals(verified.getData());
    }
}
