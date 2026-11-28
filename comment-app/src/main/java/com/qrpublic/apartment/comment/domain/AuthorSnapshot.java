package com.qrpublic.apartment.comment.domain;

/** One resolved author identity: the name shown (and hovered) plus the picture. */
public record AuthorSnapshot(String displayName, String dataUrl) {
}
