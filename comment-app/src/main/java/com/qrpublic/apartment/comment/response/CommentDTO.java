package com.qrpublic.apartment.comment.response;

import lombok.Data;

import java.util.List;

/**
 * One comment on the wire. Timestamps are ISO-8601 strings the frontend formats
 * dd/mm/yyyy hh:mm; the author fields drive the icon + hover-fullname rule.
 */
@Data
public class CommentDTO {
    private String uuid;
    private String parentUuid;
    /** "replying to @name" for replies whose parent is not the thread root. */
    private String replyToName;
    private String targetType;
    private String targetId;
    private String authorUsername;
    /** Full name snapshot; shown in the avatar hover tooltip. */
    private String authorDisplayName;
    /** Public avatar path served by this service (frontend falls back to initials). */
    private String avatarUrl;
    private String content;
    private boolean deleted;
    /** ISO-8601 creation time, displayed next to the author. */
    private String createdAt;
    /** Non-null once the author edited the comment ("(edited)" marker). */
    private String editedAt;
    private long replyCount;
    /** Root rows only: first replies shown collapsed under the root. */
    private List<CommentDTO> previewReplies;
    private List<CommentAttachmentDTO> attachments;
}
