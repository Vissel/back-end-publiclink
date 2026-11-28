package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatAttachmentModel;

/**
 * Attachment metadata plus the stored bytes, ready to serve. Built only after
 * the caller passed the ownership gate of the download endpoints.
 */
public record ChatAttachmentDownload(ChatAttachmentModel attachment, byte[] bytes) {
}
