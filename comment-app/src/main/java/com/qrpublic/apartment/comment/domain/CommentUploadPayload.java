package com.qrpublic.apartment.comment.domain;

/** One multipart upload read into memory at the controller edge. */
public record CommentUploadPayload(byte[] bytes, String fileName, String contentType) {
}
