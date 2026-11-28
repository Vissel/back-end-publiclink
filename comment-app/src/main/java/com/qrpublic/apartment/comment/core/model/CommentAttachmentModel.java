package com.qrpublic.apartment.comment.core.model;

import lombok.Data;

/**
 * Detached attachment metadata for the download path (mirrors
 * chat-app's ChatAttachmentModel; the storage key stays server-side).
 */
@Data
public class CommentAttachmentModel {
    private String uuid;
    private AttachmentKind kind;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String storageKey;
}
