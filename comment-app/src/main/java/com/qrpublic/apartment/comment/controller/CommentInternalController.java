package com.qrpublic.apartment.comment.controller;

import com.qrpublic.apartment.comment.service.CommentService;
import com.qrpublic.apartment.comment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Server-side-only surface (mirrors chat-app's ChatInternalController): the
 * gateway denies /comment/internal/** outright, so these routes are reachable
 * exclusively from other services on the same host.
 *
 * Moderation delete bypasses the author check on purpose — an administrator
 * removing spam from a public product page is not the comment's author.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/comments")
public class CommentInternalController {

    @Autowired
    CommentService commentService;

    @DeleteMapping("/{uuid}")
    public Mono<ResponseEntity<Void>> moderatorDelete(@PathVariable String uuid) {
        return Mono.fromCallable(() -> commentService.deleteAsModerator(uuid))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Comment counts of several targets in one call, for list pages (e.g. a
     * future post index showing "N comments" per row). Key of the response map
     * is "{targetType}:{targetId}"; absent keys mean the target has no thread.
     */
    @PostMapping("/counts")
    public Mono<ResponseEntity<Map<String, Long>>> counts(@RequestBody List<CommentService.CountKey> keys) {
        return Mono.fromCallable(() -> commentService.counts(keys))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
