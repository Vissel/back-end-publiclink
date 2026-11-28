package com.qrpublic.apartment.comment.core.service;

import com.qrpublic.apartment.comment.core.model.AttachmentKind;
import com.qrpublic.apartment.comment.core.model.CommentAttachmentModel;
import com.qrpublic.apartment.comment.entity.CommentAttachmentEntity;
import com.qrpublic.apartment.comment.entity.CommentEntity;
import com.qrpublic.apartment.comment.repository.CommentAttachmentRepository;
import com.qrpublic.apartment.comment.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transactional data access of comment rows. Like chat-app's CoreChatService it
 * returns detached entities (all needed columns are simple fields; the thread
 * is fetched by root id, so no lazy association is ever traversed).
 */
@Service
public class CoreCommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public Optional<CommentEntity> findByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        return commentRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public List<CommentEntity> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : commentRepository.findAllById(ids);
    }

    /** Rows of one grouped (targetId, count) result of a live-comment count. */
    @Transactional(readOnly = true)
    public List<Object[]> countLiveByTarget(String targetType, List<String> targetIds) {
        return targetIds.isEmpty() ? List.of() : commentRepository.countLiveByTarget(targetType, targetIds);
    }

    @Transactional(readOnly = true)
    public List<CommentEntity> findRootPage(String targetType, String targetId,
            Timestamp cursorTime, Long cursorId, int limit) {
        if (cursorTime == null) {
            return commentRepository.findFirstRootPage(targetType, targetId,
                    org.springframework.data.domain.PageRequest.of(0, limit));
        }
        return commentRepository.findRootPage(targetType, targetId, cursorTime,
                cursorId == null ? 0L : cursorId,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<CommentEntity> findThreadReplies(Long rootId, Long afterId, int limit) {
        return commentRepository.findThreadReplies(rootId, afterId == null ? 0L : afterId,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** Live (non-deleted) reply counts of a page of roots, one grouped query. */
    @Transactional(readOnly = true)
    public Map<Long, Long> replyCounts(List<Long> rootIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (rootIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : commentRepository.countRepliesForRoots(rootIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<CommentEntity> previewReplies(List<Long> rootIds) {
        return rootIds.isEmpty() ? List.of() : commentRepository.findPreviewReplies(rootIds);
    }

    /**
     * Flushed on purpose: the follow-up bump statement and any same-transaction
     * listing query must see the row (and its rootId) already on the DB.
     */
    @Transactional
    public CommentEntity save(CommentEntity entity) {
        return commentRepository.saveAndFlush(entity);
    }

    /** The bump statement; runs in its own short transaction. */
    @Transactional
    public void bumpRoot(Long rootId, Timestamp when) {
        commentRepository.bumpRoot(rootId, when);
    }

    @Transactional(readOnly = true)
    public Optional<CommentAttachmentEntity> findAttachmentByUuid(String uuid) {
        return attachmentRepository.findByUuid(uuid);
    }

    /** Detached metadata for the download path (chat-app's toModel arrangement). */
    @Transactional(readOnly = true)
    public Optional<CommentAttachmentModel> findAttachmentModelByUuid(String uuid) {
        return attachmentRepository.findByUuid(uuid).map(entity -> {
            CommentAttachmentModel model = new CommentAttachmentModel();
            model.setUuid(entity.getUuid());
            model.setKind(AttachmentKind.valueOf(entity.getKind()));
            model.setFileName(entity.getFileName());
            model.setMimeType(entity.getMimeType());
            model.setSizeBytes(entity.getSizeBytes());
            model.setStorageKey(entity.getStorageKey());
            return model;
        });
    }

    @Transactional(readOnly = true)
    public List<CommentAttachmentEntity> findAttachments(List<Long> commentIds) {
        return commentIds.isEmpty() ? List.of()
                : attachmentRepository.findByCommentIdInOrderByIdAsc(commentIds);
    }

    @Transactional
    public CommentAttachmentEntity saveAttachment(CommentAttachmentEntity entity) {
        return attachmentRepository.save(entity);
    }
}
