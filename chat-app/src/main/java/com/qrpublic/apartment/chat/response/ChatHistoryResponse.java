package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * History page for a conversation. {@code messages} after a given id is the
 * replay
 * a reconnecting visitor needs to catch up on everything missed while offline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {

    private String conversationUuid;

    private List<ChatMessageResponse> messages;

    /**
     * Highest message id included; pass it back as afterMessageId on the next poll.
     */
    private Long lastMessageId;

    private boolean complete;
}
