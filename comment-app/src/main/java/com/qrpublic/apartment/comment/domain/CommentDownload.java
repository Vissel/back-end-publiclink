package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.CommentAttachmentModel;

/**
 * Attachment metadata plus the stored bytes, ready to serve. Built only by the
 * download path after the uuid lookup succeeded.
 */
public record CommentDownload(CommentAttachmentModel attachment, byte[] bytes) {
}
