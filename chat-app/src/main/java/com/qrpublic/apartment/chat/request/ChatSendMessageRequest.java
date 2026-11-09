package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A message typed by the visitor in the app chat.
 *
 * {@code clientMessageId} makes a retried POST idempotent: the same value
 * returns
 * the stored message instead of creating a duplicate and dispatching it twice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendMessageRequest {

    private String conversationUuid;

    private String deviceId;

    private String clientMessageId;

    private String content;
}
