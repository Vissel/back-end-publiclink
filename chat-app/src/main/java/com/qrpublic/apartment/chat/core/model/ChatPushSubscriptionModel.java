package com.qrpublic.apartment.chat.core.model;

import lombok.Data;

import java.sql.Timestamp;

/**
 * Core-layer view of one browser push subscription.
 */
@Data
public class ChatPushSubscriptionModel {
    private Long id;
    private String adminUsername;
    private String endpoint;
    private String p256dh;
    private String auth;
    private Timestamp lastSeenAt;
}
