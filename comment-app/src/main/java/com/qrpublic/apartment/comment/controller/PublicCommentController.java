package com.qrpublic.apartment.comment.controller;

import com.qrpublic.apartment.comment.exception.ApplicationException;
import com.qrpublic.apartment.comment.response.AvatarResponse;
import com.qrpublic.apartment.comment.response.CommentHealthResponse;
import com.qrpublic.apartment.comment.response.ReplyListResponse;
import com.qrpublic.apartment.comment.response.ThreadResponse;
import com.qrpublic.apartment.comment.service.CommentService;
import com.qrpublic.apartment.comment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Public (permit-all at the gateway) read half of the comment API, mirroring
 * chat-app's split: anonymous buyers on a sale link can see the discussion,
 * while every write lives in {@link CommentWriteController} behind the JWT.
 *
 * Blocking JPA work runs on boundedElastic; the reactive layer only carries it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
public class PublicCommentController {

    @Autowired
    CommentService commentService;

    /** Public liveness probe used by the deployment health checks. */
    @GetMapping("/health")
    public Mono<ResponseEntity<CommentHealthResponse>> health() {
        return Mono.fromCallable(commentService::health)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** One page of roots for a target, newest activity first. */
    @GetMapping("/comments")
    public Mono<ResponseEntity<ThreadResponse>> list(
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> commentService.listThreads(targetType, targetId, cursor, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** One thread's replies, chronological, keyset-paged by the last seen id. */
    @GetMapping("/comments/{uuid}/replies")
    public Mono<ResponseEntity<ReplyListResponse>> replies(
            @PathVariable String uuid,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> commentService.listReplies(uuid, afterId, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** Cached author avatar for the comment icon; 404 means "render initials". */
    @GetMapping("/avatar/{username}")
    public Mono<ResponseEntity<AvatarResponse>> avatar(@PathVariable String username) {
        return Mono.fromCallable(() -> commentService.avatar(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** Attachment bytes; the unguessable uuid is the capability. */
    @GetMapping("/attachment/{uuid}")
    public Mono<ResponseEntity<byte[]>> downloadAttachment(@PathVariable String uuid) {
        return Mono.fromCallable(() -> commentService.downloadAttachment(uuid))
                .subscribeOn(Schedulers.boundedElastic())
                .map(CommentAttachmentHttp::downloadResponse)
                .onErrorResume(ApplicationException.class, error -> Mono.just(CommentAttachmentHttp.error(error)));
    }
}
