package com.qrpublic.apartment.comment.service;

import com.qrpublic.apartment.comment.convertor.CommentConvertor;
import com.qrpublic.apartment.comment.core.model.CommentTargetType;
import com.qrpublic.apartment.comment.core.model.CommentView;
import com.qrpublic.apartment.comment.core.service.CoreCommentService;
import com.qrpublic.apartment.comment.domain.AuthorProfileDomain;
import com.qrpublic.apartment.comment.domain.CommentAttachmentDomain;
import com.qrpublic.apartment.comment.domain.CommentDomain;
import com.qrpublic.apartment.comment.domain.CommentDownload;
import com.qrpublic.apartment.comment.domain.CommentUploadPayload;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import com.qrpublic.apartment.comment.request.CreateCommentRequest;
import com.qrpublic.apartment.comment.request.EditCommentRequest;
import com.qrpublic.apartment.comment.response.AvatarResponse;
import com.qrpublic.apartment.comment.response.CommentDTO;
import com.qrpublic.apartment.comment.response.CommentHealthResponse;
import com.qrpublic.apartment.comment.response.ReplyListResponse;
import com.qrpublic.apartment.comment.response.ThreadResponse;
import com.qrpublic.apartment.comment.template.model.Result;
import com.qrpublic.apartment.comment.template.service.CommentServiceTemplate;
import com.qrpublic.apartment.comment.template.service.ProcessCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * The comment module's service layer on the shared template: every method
 * validates in preProcess, delegates the work to the domain in process, and
 * returns the {@link Result} error contract the controllers map onto HTTP.
 *
 * Reads are public; writes take the author ONLY from the gateway-forwarded
 * X-User-ID header the controller passes down here.
 */
@Slf4j
@Service
public class CommentService {

    @Autowired
    private CommentServiceTemplate commentServiceTemplate;

    @Autowired
    private CommentDomain commentDomain;

    @Autowired
    private AuthorProfileDomain authorProfileDomain;

    @Autowired
    private CommentAttachmentDomain attachmentDomain;

    @Autowired
    private CoreCommentService coreCommentService;

    @Value("${comment.max-content-length:5000}")
    private int maxContentLength;

    @Value("${comment.max-file-bytes:10485760}")
    private long maxFileBytes;

    // -------------------------------------------------------------------- reads

    /** Public liveness probe (routes + service up); carries no business data. */
    public Result<CommentHealthResponse> health() {
        return Result.success(new CommentHealthResponse("UP", "comment-app", "/comment"));
    }

    public Result<ThreadResponse> listThreads(String targetTypeRaw, String targetId,
            String cursor, Integer limitRaw) {
        return commentServiceTemplate.execute(new ProcessCallback<String, ThreadResponse>() {
            @Override
            public String getRequest() {
                return targetTypeRaw + "/" + targetId;
            }

            @Override
            public void preProcess(String request) {
                Assert.hasText(targetId, "targetId is required.");
                Assert.isTrue(targetId.length() <= 64, "targetId is too long.");
                Assert.notNull(resolveTargetType(targetTypeRaw), "Unknown targetType.");
            }

            @Override
            public ThreadResponse process() {
                var page = commentDomain.listRoots(resolveTargetType(targetTypeRaw), targetId,
                        cursor, clamp(limitRaw, 20, 1, 50));
                ThreadResponse response = new ThreadResponse();
                response.setItems(CommentConvertor.toDTOs(page.items()));
                response.setNextCursor(page.nextCursor());
                return response;
            }
        });
    }

    public Result<ReplyListResponse> listReplies(String rootUuid, Long afterId, Integer limitRaw) {
        return commentServiceTemplate.execute(new ProcessCallback<String, ReplyListResponse>() {
            @Override
            public String getRequest() {
                return rootUuid;
            }

            @Override
            public void preProcess(String uuid) {
                Assert.hasText(uuid, "Comment uuid is required.");
            }

            @Override
            public ReplyListResponse process() {
                List<CommentView> replies = commentDomain.listReplies(rootUuid, afterId,
                        clamp(limitRaw, 50, 1, 100));
                ReplyListResponse response = new ReplyListResponse();
                response.setReplies(CommentConvertor.toDTOs(replies));
                return response;
            }
        });
    }

    /** Cached avatar of one author; a 404 simply means "render initials". */
    public Result<AvatarResponse> avatar(String username) {
        return commentServiceTemplate.execute(new ProcessCallback<String, AvatarResponse>() {
            @Override
            public String getRequest() {
                return username;
            }

            @Override
            public void preProcess(String name) {
                Assert.hasText(name, "Username is required.");
            }

            @Override
            public AvatarResponse process() {
                String dataUrl = authorProfileDomain.cachedAvatar(username);
                if (dataUrl == null) {
                    throw new ApplicationException("No avatar stored for this author.", 404);
                }
                AvatarResponse response = new AvatarResponse();
                response.setUsername(username);
                response.setDataUrl(dataUrl);
                return response;
            }
        });
    }

    /**
     * Bytes of one attachment. Comments (and therefore their attachments) are
     * public, so the unguessable uuid in the URL is the capability.
     */
    public CommentDownload downloadAttachment(String uuid) {
        var attachment = coreCommentService.findAttachmentModelByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Attachment not found.", 404));
        return new CommentDownload(attachment, attachmentDomain.load(attachment.getStorageKey()));
    }

    // ------------------------------------------------------------------- writes

    public Result<CommentDTO> create(CreateCommentRequest request,
            List<CommentUploadPayload> uploads, String callerUsername) {
        return commentServiceTemplate.execute(new ProcessCallback<CreateCommentRequest, CommentDTO>() {
            @Override
            public CreateCommentRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(CreateCommentRequest request) {
                Assert.notNull(request, "A comment body is required.");
                Assert.hasText(request.getTargetId(), "targetId is required.");
                Assert.notNull(resolveTargetType(request.getTargetType()), "Unknown targetType.");
                Assert.isTrue(request.getContent() == null || request.getContent().length() <= maxContentLength,
                        "Comment is longer than " + maxContentLength + " characters.");
                if (uploads != null) {
                    for (CommentUploadPayload upload : uploads) {
                        Assert.isTrue(upload == null || upload.bytes() == null
                                        || upload.bytes().length <= maxFileBytes,
                                "An uploaded file is larger than the allowed limit.");
                    }
                }
            }

            @Override
            public CommentDTO process() {
                CommentView view = commentDomain.create(
                        resolveTargetType(request.getTargetType()),
                        request.getTargetId().trim(),
                        request.getParentUuid(),
                        request.getContent(),
                        uploads,
                        callerUsername);
                return CommentConvertor.toDTO(view);
            }
        });
    }

    public Result<CommentDTO> edit(String uuid, EditCommentRequest request, String callerUsername) {
        return commentServiceTemplate.execute(new ProcessCallback<String, CommentDTO>() {
            @Override
            public String getRequest() {
                return uuid;
            }

            @Override
            public void preProcess(String commentUuid) {
                Assert.hasText(commentUuid, "Comment uuid is required.");
                Assert.notNull(request, "A body with the new content is required.");
            }

            @Override
            public CommentDTO process() {
                return CommentConvertor.toDTO(commentDomain.edit(uuid, request.getContent(), callerUsername));
            }
        });
    }

    public Result<Void> delete(String uuid, String callerUsername) {
        return commentServiceTemplate.execute(new ProcessCallback<String, Void>() {
            @Override
            public String getRequest() {
                return uuid;
            }

            @Override
            public void preProcess(String commentUuid) {
                Assert.hasText(commentUuid, "Comment uuid is required.");
            }

            @Override
            public Void process() {
                commentDomain.delete(uuid, callerUsername);
                return null;
            }
        });
    }

    /** Moderation delete, reachable only through the gateway-denied internal route. */
    public Result<Void> deleteAsModerator(String uuid) {
        return commentServiceTemplate.execute(new ProcessCallback<String, Void>() {
            @Override
            public String getRequest() {
                return uuid;
            }

            @Override
            public void preProcess(String commentUuid) {
                Assert.hasText(commentUuid, "Comment uuid is required.");
            }

            @Override
            public Void process() {
                commentDomain.deleteAsModerator(uuid);
                return null;
            }
        });
    }

    /**
     * Live comment counts of many targets, keyed "{targetType}:{targetId}".
     * Internal endpoint only; a target without a thread is simply absent.
     */
    public Result<Map<String, Long>> counts(List<CountKey> keys) {
        return commentServiceTemplate.execute(new ProcessCallback<List<CountKey>, Map<String, Long>>() {
            @Override
            public List<CountKey> getRequest() {
                return keys;
            }

            @Override
            public void preProcess(List<CountKey> request) {
                Assert.notNull(request, "A list of targets is required.");
                Assert.isTrue(request.size() <= 200, "At most 200 targets per call.");
            }

            @Override
            public Map<String, Long> process() {
                Map<String, List<String>> idsByType = keys.stream()
                        .filter(key -> key != null && resolveTargetType(key.targetType()) != null
                                && key.targetId() != null && !key.targetId().isBlank())
                        .collect(java.util.stream.Collectors.groupingBy(
                                key -> resolveTargetType(key.targetType()).name(),
                                java.util.stream.Collectors.mapping(
                                        key -> key.targetId().trim(), java.util.stream.Collectors.toList())));

                Map<String, Long> counts = new java.util.HashMap<>();
                idsByType.forEach((type, ids) -> {
                    for (Object[] row : coreCommentService.countLiveByTarget(type, ids.stream().distinct().toList())) {
                        counts.put(type + ":" + row[0], (Long) row[1]);
                    }
                });
                return counts;
            }
        });
    }

    /** One (targetType, targetId) pair of a count lookup. */
    public record CountKey(String targetType, String targetId) {
    }

    // ------------------------------------------------------------------ helpers

    private static CommentTargetType resolveTargetType(String raw) {
        return CommentTargetType.from(raw);
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
