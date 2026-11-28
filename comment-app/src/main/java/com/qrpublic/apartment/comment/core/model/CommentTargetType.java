package com.qrpublic.apartment.comment.core.model;

/**
 * What a comment thread is attached to. PRODUCT exists today (target_id is the
 * publiclink-db product id as a string); POST and GUIDELINE land with their
 * own pages and need no backend change. Stored as a plain string column.
 */
public enum CommentTargetType {
    PRODUCT,
    POST,
    GUIDELINE;

    public static CommentTargetType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
