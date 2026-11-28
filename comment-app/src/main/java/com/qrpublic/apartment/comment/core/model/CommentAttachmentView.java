package com.qrpublic.apartment.comment.core.model;

import lombok.Data;

/** Attachment metadata as delivered to clients (never the storage key). */
@Data
public class CommentAttachmentView {
    private String uuid;
    private AttachmentKind kind;
    private String fileName;
    private String mimeType;
    private long sizeBytes;
}
