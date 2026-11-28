package com.qrpublic.apartment.comment.request;

import lombok.Data;

/**
 * POST body of a new comment (multipart form fields; files travel as separate
 * FileParts). No author field on purpose: identity comes ONLY from the
 * gateway-forwarded X-User-ID header.
 */
@Data
public class CreateCommentRequest {
    private String targetType;
    private String targetId;
    /** UUID of the comment being replied to; null/blank for a new root. */
    private String parentUuid;
    private String content;
}
