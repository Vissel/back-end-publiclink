package com.qrpublic.apartment.comment.request;

import lombok.Data;

/** PUT body of an author edit; attachments are immutable once posted. */
@Data
public class EditCommentRequest {
    private String content;
}
