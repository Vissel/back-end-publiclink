package com.qrpublic.apartment.comment.response;

import lombok.Data;

import java.util.List;

/** One page of root comments; pass {@code nextCursor} back to load the next. */
@Data
public class ThreadResponse {
    private List<CommentDTO> items;
    private String nextCursor;
}
