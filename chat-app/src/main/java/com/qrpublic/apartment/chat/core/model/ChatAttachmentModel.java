package com.qrpublic.apartment.chat.core.model;

import lombok.Data;

/**
 * Core-layer view of a chat message attachment. The kind drives both the
 * client rendering (thumbnail vs download row) and the download response
 * headers (inline vs attachment).
 */
@Data
public class ChatAttachmentModel {
    private Long id;
    private String uuid;
    private Long messageId;
    private ChatAttachmentKind kind;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String storageKey;
}
