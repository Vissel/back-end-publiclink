package com.qrpublic.apartment.chat.core.model;

import lombok.Data;

import java.sql.Timestamp;

/**
 * Core-layer view of a chat message. Domains and services consume this instead
 * of the JPA entity so the lazy conversation proxy never leaks upward.
 */
@Data
public class ChatMessageModel {
    private Long id;
    private Long conversationId;
    private String conversationUuid;
    private String deviceId;
    private String clientTag;
    private String clientMessageId;
    private ChatDirectionEnum direction;
    private ChatStatusEnum status;
    private String content;
    private String zaloMessageId;
    private Long quoteMessageId;
    private Integer errorCode;
    private Timestamp createdAt;
    private Timestamp deliveredAt;
    private Timestamp seenAt;
}
