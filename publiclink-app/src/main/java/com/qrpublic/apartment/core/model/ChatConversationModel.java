package com.qrpublic.apartment.core.model;

import lombok.Data;

import java.sql.Timestamp;

/**
 * Core-layer view of a conversation.
 */
@Data
public class ChatConversationModel {
    private Long id;
    private String uuid;
    private String deviceId;
    private String status;
    private String clientTag;
    private Timestamp createdAt;
    private Timestamp lastMessageAt;
    private long clientMessageCount;
    private long adminMessageCount;
    /** True while the newest message overall comes from the client. */
    private boolean awaitingAdmin;
}
