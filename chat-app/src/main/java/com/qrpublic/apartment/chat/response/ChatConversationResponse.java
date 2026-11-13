package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conversation summary for the administrator inbox and for the client's own
 * header.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationResponse {

    private String uuid;

    /** Short customer label used in the Zalo thread, e.g. 0042. */
    private String clientTag;

    private String status;

    private String deviceId;

    /**
     * How the current visit was tied to this device: NEW / DEVICE_ID / FINGERPRINT
     * / IP.
     */
    private String recognition;

    private Integer visitCount;

    private String createdAt;

    private String lastMessageAt;

    private long clientMessageCount;

    private long adminMessageCount;

    /** True while the newest message is still waiting for the administrator. */
    private boolean awaitingAdmin;

    /** Guest messages the administrator has not opened; drives the inbox badge. */
    private Integer unreadCount;

    /** Signed-in account behind the thread; null for an anonymous visitor. */
    private String ownerUsername;

    /** Human name shown in the console instead of "Guest #tag" when known. */
    private String ownerDisplayName;

    /** Preview of the latest message, truncated for list rendering. */
    private String lastMessagePreview;
}
