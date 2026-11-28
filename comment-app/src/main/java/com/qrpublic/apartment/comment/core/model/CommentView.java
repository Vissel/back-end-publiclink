package com.qrpublic.apartment.comment.core.model;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * Internal view of one comment, entity-free and ready for the response DTO.
 * A root additionally carries its live reply count and the first preview
 * replies; a reply carries its attachments and the name of the comment it
 * addresses (the "replying to @name" line for deep threads).
 */
@Data
public class CommentView {
    /** Internal row id (cursor and attachment grouping only; never exposed). */
    private Long id;
    private String uuid;
    private String parentUuid;
    /** Name snapshot of the direct parent's author; set for replies only. */
    private String replyToName;
    private String targetType;
    private String targetId;
    private String authorUsername;
    private String authorDisplayName;
    private String content;
    private boolean deleted;
    private Timestamp createdAt;
    private Timestamp editedAt;
    /** Root rows only: the bump key, used to build the listing cursor. */
    private Timestamp lastActivityAt;
    private long replyCount;
    private List<CommentView> previewReplies;
    private List<CommentAttachmentView> attachments;
}
