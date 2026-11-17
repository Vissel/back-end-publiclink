package com.qrpublic.apartment.chat.core.model;

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
    /** Guest messages not yet opened by the administrator. */
    private int unreadCount;
    /** Signed-in owner of the thread, null for anonymous visitors. */
    private String ownerUsername;
    /** Human name of the owner; falls back to the username when unknown. */
    private String ownerDisplayName;
    /** Capability secret of the visitor's event stream. */
    private String streamKey;
    /** True while the newest message overall comes from the client. */
    private boolean awaitingAdmin;
}
