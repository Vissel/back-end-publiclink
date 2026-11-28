package com.qrpublic.apartment.chat.request;

/**
 * One multipart upload read into memory with a hard byte cap, on its way from
 * a controller into the attachment domain. The cap is the document limit; the
 * per-kind (image/file) limit is enforced again inside the domain.
 */
public record ChatUploadPayload(byte[] bytes, String fileName, String contentType) {
}
