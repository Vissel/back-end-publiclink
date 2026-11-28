package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.CommentView;

import java.util.List;

/** One page of root comments plus the keyset cursor for the next page. */
public record ThreadPage(List<CommentView> items, String nextCursor) {
}
