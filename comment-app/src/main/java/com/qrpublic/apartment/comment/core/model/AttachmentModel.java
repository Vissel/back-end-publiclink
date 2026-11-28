package com.qrpublic.apartment.comment.core.model;

import lombok.Data;

/**
 * Validated, stored attachment metadata before the comment row exists. Mirrors
 * chat-app's ChatAttachmentModel minus the message link: the caller stores the
 * comment first, then writes this as a comment_attachment row.
 */
@Data
public class AttachmentModel {
    private AttachmentKind kind;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String storageKey;
}
