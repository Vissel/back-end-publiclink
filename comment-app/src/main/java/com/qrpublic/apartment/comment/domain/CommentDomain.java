package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.AttachmentKind;
import com.qrpublic.apartment.comment.core.model.AttachmentModel;
import com.qrpublic.apartment.comment.core.model.CommentAttachmentView;
import com.qrpublic.apartment.comment.core.model.CommentTargetType;
import com.qrpublic.apartment.comment.core.model.CommentView;
import com.qrpublic.apartment.comment.core.service.CoreCommentService;
import com.qrpublic.apartment.comment.entity.CommentAttachmentEntity;
import com.qrpublic.apartment.comment.entity.CommentEntity;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The comment tree itself: posting, bumping, listing, editing, deleting.
 *
 * Ordering rules implemented here:
 * - Newest first at the top level: roots are listed by last_activity_at DESC.
 * - A reply lifts its WHOLE tree: posting any reply refreshes the root's
 *   last_activity_at (CommentRepository.bumpRoot), so the thread — including
 *   every deeper sub-reply — moves to the top as one unit.
 * - Inside a thread replies are chronological (oldest first), like a chat.
 *
 * Identity: the author is the gateway-forwarded X-User-ID, never a body field.
 * Edit and delete are author-only; the internal admin endpoint bypasses that
 * check itself, not this domain.
 */
@Slf4j
@Component
public class CommentDomain {

    /** Replies shown under a root before "show more replies" kicks in. */
    private static final int PREVIEW_REPLY_LIMIT = 3;

    @Autowired
    private CoreCommentService coreCommentService;

    @Autowired
    private CommentAttachmentDomain attachmentDomain;

    @Autowired
    private AuthorProfileDomain authorProfileDomain;

    @Value("${comment.max-content-length:5000}")
    private int maxContentLength;

    @Value("${comment.max-attachments:4}")
    private int maxAttachments;

    // ------------------------------------------------------------------ listing

    /**
     * One page of roots for a target, newest activity first, each root carrying
     * its live reply count and first preview replies. Cursor is
     * "{lastActivityMillis}:{rootId}"; the first page passes null.
     */
    public ThreadPage listRoots(CommentTargetType targetType, String targetId, String cursor, int limit) {
        Timestamp cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            int colon = cursor.indexOf(':');
            try {
                cursorTime = new Timestamp(Long.parseLong(cursor.substring(0, colon)));
                cursorId = Long.parseLong(cursor.substring(colon + 1));
            } catch (RuntimeException e) {
                throw new ApplicationException("Invalid cursor.", 400);
            }
        }

        List<CommentEntity> roots = coreCommentService.findRootPage(
                targetType.name(), targetId, cursorTime, cursorId, limit);
        if (roots.isEmpty()) {
            return new ThreadPage(List.of(), null);
        }

        List<Long> rootIds = roots.stream().map(CommentEntity::getId).toList();
        Map<Long, Long> counts = coreCommentService.replyCounts(rootIds);
        Map<Long, List<CommentEntity>> previewsByRoot = groupPreviews(coreCommentService.previewReplies(rootIds));
        List<CommentEntity> previewRows = previewsByRoot.values().stream().flatMap(List::stream).toList();
        ParentIndex parents = indexParents(previewRows);

        List<CommentView> items = new ArrayList<>(roots.size());
        for (CommentEntity root : roots) {
            CommentView view = toView(root, parents);
            view.setReplyCount(counts.getOrDefault(root.getId(), 0L));
            view.setAttachments(attachmentViews(
                    coreCommentService.findAttachments(List.of(root.getId()))));
            List<CommentView> previews = previewsByRoot.getOrDefault(root.getId(), List.of()).stream()
                    .limit(PREVIEW_REPLY_LIMIT)
                    .map(reply -> toView(reply, parents))
                    .toList();
            attach(previews);
            view.setPreviewReplies(previews);
            items.add(view);
        }

        CommentEntity last = roots.get(roots.size() - 1);
        String nextCursor = roots.size() == limit
                ? last.getLastActivityAt().getTime() + ":" + last.getId()
                : null;
        return new ThreadPage(items, nextCursor);
    }

    /** One thread's replies, chronological, keyset-paged by comment id. */
    public List<CommentView> listReplies(String rootUuid, Long afterId, int limit) {
        CommentEntity root = requireRoot(rootUuid);
        List<CommentEntity> replies = coreCommentService.findThreadReplies(root.getId(), afterId, limit);
        ParentIndex parents = indexParents(replies);
        List<CommentView> views = replies.stream()
                .map(reply -> toView(reply, parents))
                .toList();
        attach(views);
        return views;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Posts one comment or reply. Attachments are validated and written BEFORE
     * the comment row exists (a refused upload never leaves a half comment),
     * the row follows, then the metadata rows and the root bump.
     */
    public CommentView create(CommentTargetType targetType, String targetId, String parentUuid,
            String rawContent, List<CommentUploadPayload> uploads, String authorUsername) {
        if (authorUsername == null || authorUsername.isBlank()) {
            throw new ApplicationException("Authentication is required.", 401);
        }
        CommentEntity parent = null;
        if (parentUuid != null && !parentUuid.isBlank()) {
            parent = coreCommentService.findByUuid(parentUuid)
                    .orElseThrow(() -> new ApplicationException("The comment being replied to no longer exists.", 404));
            if (!parent.getTargetType().equals(targetType.name()) || !parent.getTargetId().equals(targetId)) {
                throw new ApplicationException("The reply targets a different object.", 400);
            }
        }

        String content = rawContent == null ? "" : rawContent.trim();
        boolean hasUploads = uploads != null && !uploads.isEmpty();
        if (content.isEmpty() && !hasUploads) {
            throw new ApplicationException("Write something or attach a file.", 400);
        }
        if (content.length() > maxContentLength) {
            throw new ApplicationException("Comment is longer than " + maxContentLength + " characters.", 400);
        }
        if (hasUploads && uploads.size() > maxAttachments) {
            throw new ApplicationException("At most " + maxAttachments + " attachments per comment.", 400);
        }

        List<AttachmentModel> stored = hasUploads
                ? uploads.stream()
                        .map(upload -> attachmentDomain.store(upload.bytes(), upload.fileName(), upload.contentType()))
                        .toList()
                : List.of();

        AuthorSnapshot author = authorProfileDomain.resolveSnapshot(authorUsername);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        CommentEntity entity = new CommentEntity();
        entity.setUuid(UUID.randomUUID().toString());
        entity.setTargetType(targetType.name());
        entity.setTargetId(targetId);
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setAuthorUsername(authorUsername);
        entity.setAuthorDisplayName(truncate(author.displayName(), 255));
        entity.setContent(content.isEmpty() ? placeholder(stored) : content);
        entity.setDeleted(false);
        entity.setCreatedAt(now);
        entity.setLastActivityAt(now);
        CommentEntity saved = coreCommentService.save(entity);

        // rootId needs the generated id: a root owns its thread, a reply joins
        // the parent's root.
        Long rootId = parent == null ? saved.getId() : parent.getRootId();
        saved.setRootId(rootId);
        coreCommentService.save(saved);

        List<CommentAttachmentEntity> rows = new ArrayList<>(stored.size());
        for (AttachmentModel model : stored) {
            CommentAttachmentEntity attachment = new CommentAttachmentEntity();
            attachment.setUuid(UUID.randomUUID().toString());
            attachment.setCommentId(saved.getId());
            attachment.setKind(model.getKind().name());
            attachment.setFileName(model.getFileName());
            attachment.setMimeType(model.getMimeType());
            attachment.setSizeBytes(model.getSizeBytes());
            attachment.setStorageKey(model.getStorageKey());
            attachment.setCreatedAt(now);
            rows.add(coreCommentService.saveAttachment(attachment));
        }

        if (parent != null) {
            // The bump: the whole tree rises to the top of the listing.
            coreCommentService.bumpRoot(rootId, now);
        }
        log.info("Comment {} posted on {}#{} by {} (reply={})",
                saved.getUuid(), targetType, targetId, authorUsername, parent != null);

        CommentView view = toView(saved, ParentIndex.EMPTY);
        view.setParentUuid(parent == null ? null : parent.getUuid());
        view.setReplyCount(0);
        view.setAttachments(attachmentViews(rows));
        if (parent != null) {
            view.setReplyToName(parent.getAuthorDisplayName());
        }
        return view;
    }

    /** Author-only content edit; attachments stay immutable once posted. */
    public CommentView edit(String uuid, String rawContent, String callerUsername) {
        CommentEntity entity = coreCommentService.findByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Comment not found.", 404));
        requireAuthor(entity, callerUsername);
        if (entity.isDeleted()) {
            throw new ApplicationException("This comment was deleted.", 404);
        }
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw new ApplicationException("Comment content is required.", 400);
        }
        if (content.length() > maxContentLength) {
            throw new ApplicationException("Comment is longer than " + maxContentLength + " characters.", 400);
        }
        entity.setContent(content);
        entity.setEditedAt(new Timestamp(System.currentTimeMillis()));
        return toView(coreCommentService.save(entity), ParentIndex.EMPTY);
    }

    /**
     * Author-only soft delete. The row stays so children remain reachable; a
     * deleted root with live replies renders as a placeholder.
     */
    public void delete(String uuid, String callerUsername) {
        CommentEntity entity = coreCommentService.findByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Comment not found.", 404));
        requireAuthor(entity, callerUsername);
        softDelete(entity);
    }

    /** Moderation delete used only by the gateway-denied internal endpoint. */
    public void deleteAsModerator(String uuid) {
        CommentEntity entity = coreCommentService.findByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Comment not found.", 404));
        softDelete(entity);
    }

    private void softDelete(CommentEntity entity) {
        if (entity.isDeleted()) {
            return;
        }
        entity.setDeleted(true);
        entity.setContent("");
        coreCommentService.save(entity);
        log.info("Comment {} deleted by {}", entity.getUuid(), entity.getAuthorUsername());
    }

    private void requireAuthor(CommentEntity entity, String callerUsername) {
        if (callerUsername == null || callerUsername.isBlank()) {
            throw new ApplicationException("Authentication is required.", 401);
        }
        if (!entity.getAuthorUsername().equals(callerUsername)) {
            throw new ApplicationException("Only the author can change their comment.", 403);
        }
    }

    // ------------------------------------------------------------------ mapping

    private CommentEntity requireRoot(String rootUuid) {
        CommentEntity root = coreCommentService.findByUuid(rootUuid)
                .orElseThrow(() -> new ApplicationException("Comment not found.", 404));
        if (root.getParentId() != null) {
            throw new ApplicationException("Not a thread root.", 400);
        }
        return root;
    }

    /** Reply preview rows grouped by their root, chronological within groups. */
    private static Map<Long, List<CommentEntity>> groupPreviews(List<CommentEntity> previews) {
        Map<Long, List<CommentEntity>> byRoot = new LinkedHashMap<>();
        for (CommentEntity reply : previews) {
            byRoot.computeIfAbsent(reply.getRootId(), id -> new ArrayList<>()).add(reply);
        }
        return byRoot;
    }

    /**
     * Direct-parent rows of the given comments, indexed by id: the parent uuid
     * (for exact tree reconstruction on the client) and the parent author's
     * name snapshot (the "replying to @name" line for deep threads).
     */
    private ParentIndex indexParents(List<CommentEntity> comments) {
        Set<Long> parentIds = new HashSet<>();
        for (CommentEntity comment : comments) {
            if (comment.getParentId() != null) {
                parentIds.add(comment.getParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return ParentIndex.EMPTY;
        }
        Map<Long, String> uuids = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (CommentEntity parent : coreCommentService.findByIds(new ArrayList<>(parentIds))) {
            uuids.put(parent.getId(), parent.getUuid());
            names.put(parent.getId(), parent.getAuthorDisplayName());
        }
        return new ParentIndex(uuids, names);
    }

    /** parent id -> parent uuid / author name, resolved in one batch. */
    private record ParentIndex(Map<Long, String> uuids, Map<Long, String> names) {
        private static final ParentIndex EMPTY = new ParentIndex(Map.of(), Map.of());
    }

    /** Loads and sets attachments on views whose rows were just listed. */
    private void attach(List<CommentView> views) {
        if (views.isEmpty()) {
            return;
        }
        List<Long> ids = views.stream().map(CommentView::getId).toList();
        Map<Long, List<CommentAttachmentView>> byComment = new HashMap<>();
        for (CommentAttachmentEntity attachment : coreCommentService.findAttachments(ids)) {
            byComment.computeIfAbsent(attachment.getCommentId(), id -> new ArrayList<>())
                    .add(toAttachmentView(attachment));
        }
        for (CommentView view : views) {
            view.setAttachments(byComment.getOrDefault(view.getId(), List.of()));
        }
    }

    private static List<CommentAttachmentView> attachmentViews(List<CommentAttachmentEntity> rows) {
        return rows.stream().map(CommentDomain::toAttachmentView).toList();
    }

    private static CommentAttachmentView toAttachmentView(CommentAttachmentEntity attachment) {
        CommentAttachmentView view = new CommentAttachmentView();
        view.setUuid(attachment.getUuid());
        view.setKind(AttachmentKind.valueOf(attachment.getKind()));
        view.setFileName(attachment.getFileName());
        view.setMimeType(attachment.getMimeType());
        view.setSizeBytes(attachment.getSizeBytes());
        return view;
    }

    /** Text shown when the visitor sent only files (chat-app's idiom). */
    private static String placeholder(List<AttachmentModel> stored) {
        AttachmentModel first = stored.get(0);
        String base = (first.getKind() == AttachmentKind.IMAGE ? "[Image] " : "[File] ") + first.getFileName();
        return stored.size() > 1 ? base + " +" + (stored.size() - 1) + " more" : base;
    }

    private static CommentView toView(CommentEntity entity, ParentIndex parents) {
        CommentView view = new CommentView();
        view.setId(entity.getId());
        view.setUuid(entity.getUuid());
        if (entity.getParentId() != null) {
            view.setParentUuid(parents.uuids().get(entity.getParentId()));
            view.setReplyToName(parents.names().get(entity.getParentId()));
        }
        view.setTargetType(entity.getTargetType());
        view.setTargetId(entity.getTargetId());
        view.setAuthorUsername(entity.getAuthorUsername());
        view.setAuthorDisplayName(entity.getAuthorDisplayName());
        view.setContent(entity.getContent());
        view.setDeleted(entity.isDeleted());
        view.setCreatedAt(entity.getCreatedAt());
        view.setEditedAt(entity.getEditedAt());
        view.setLastActivityAt(entity.getLastActivityAt());
        view.setPreviewReplies(List.of());
        view.setAttachments(List.of());
        return view;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
