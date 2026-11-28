package com.qrpublic.apartment.comment.response;

import lombok.Data;

/** One attachment as delivered to clients; the storage key never leaves. */
@Data
public class CommentAttachmentDTO {
    private String uuid;
    /** IMAGE (thumbnail) / FILE (download row). */
    private String kind;
    private String fileName;
    private String mimeType;
    private long sizeBytes;
    /** Gateway-relative download path of this attachment. */
    private String downloadUrl;
}
