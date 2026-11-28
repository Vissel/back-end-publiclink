package com.qrpublic.apartment.comment.response;

import lombok.Data;

import java.util.List;

/** Page of one thread's replies, chronological (keyset: pass last id as afterId). */
@Data
public class ReplyListResponse {
    private List<CommentDTO> replies;
}
