package com.qrpublic.apartment.comment.controller;

import com.qrpublic.apartment.comment.domain.CommentUploadPayload;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import com.qrpublic.apartment.comment.request.CreateCommentRequest;
import com.qrpublic.apartment.comment.request.EditCommentRequest;
import com.qrpublic.apartment.comment.response.CommentDTO;
import com.qrpublic.apartment.comment.service.CommentService;
import com.qrpublic.apartment.comment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Authenticated write half of the comment API. These paths are NOT in the
 * gateway permit-all list, so reaching them already required a valid JWT; the
 * author is taken ONLY from the gateway-forwarded X-User-ID header (inbound
 * values of that header are stripped by the gateway, so it cannot be forged) —
 * never from the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/comments")
public class CommentWriteController {

    private static final String USER_ID_HEADER = "X-User-ID";

    @Autowired
    CommentService commentService;

    /** Hard ceiling while reading one multipart upload into memory. */
    @Value("${comment.max-file-bytes:10485760}")
    private int maxUploadBytes;

    /**
     * Posts a comment or a reply with 0..N image/file attachments. Plain form
     * fields arrive as parts, files as FileParts; every file is capped during
     * read, then the domain re-validates kind, magic bytes and per-kind size.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<CommentDTO>> create(
            @RequestPart("targetType") String targetType,
            @RequestPart("targetId") String targetId,
            @RequestPart(value = "parentUuid", required = false) String parentUuid,
            @RequestPart(value = "content", required = false) String content,
            @RequestHeader(value = USER_ID_HEADER, required = false) String caller,
            @RequestPart(value = "files", required = false) Flux<FilePart> files) {
        // flatMap keeps upload order (concurrency 1) and unwraps the read Mono.
        Flux<CommentUploadPayload> reads = files == null
                ? Flux.empty()
                : files.flatMap(file -> CommentAttachmentHttp.readCapped(file, maxUploadBytes), 1);
        return reads
                .collectList()
                .flatMap(uploads -> Mono.fromCallable(() -> commentService.create(
                        buildRequest(targetType, targetId, parentUuid, content), uploads, caller))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity)
                .onErrorResume(ApplicationException.class, error -> Mono.just(CommentAttachmentHttp.error(error)));
    }

    /** Author-only content edit. */
    @PutMapping("/{uuid}")
    public Mono<ResponseEntity<CommentDTO>> edit(
            @PathVariable String uuid,
            @RequestBody EditCommentRequest request,
            @RequestHeader(value = USER_ID_HEADER, required = false) String caller) {
        return Mono.fromCallable(() -> commentService.edit(uuid, request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /** Author-only soft delete; children stay reachable as a placeholder. */
    @DeleteMapping("/{uuid}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable String uuid,
            @RequestHeader(value = USER_ID_HEADER, required = false) String caller) {
        return Mono.fromCallable(() -> commentService.delete(uuid, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    private static CreateCommentRequest buildRequest(
            String targetType, String targetId, String parentUuid, String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setParentUuid(blankToNull(parentUuid));
        request.setContent(content);
        return request;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
