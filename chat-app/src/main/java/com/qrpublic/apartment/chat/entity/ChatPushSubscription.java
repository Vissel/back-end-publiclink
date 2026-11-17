package com.qrpublic.apartment.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * One browser push subscription of an administrator.
 *
 * The push endpoint URL is unique by itself (it embeds the subscription id), so
 * it doubles as the key: a re-subscribe from the same browser updates the row
 * instead of accumulating duplicates.
 */
@Entity
@Table(name = "chat_push_subscription")
@Getter
@Setter
@NoArgsConstructor
public class ChatPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain username, never a foreign key: admins have no row in this schema. */
    @Column(name = "admin_username", nullable = false, length = 64)
    private String adminUsername;

    @Column(name = "endpoint", nullable = false, length = 512)
    private String endpoint;

    /** Client ECDH public key, base64url. */
    @Column(name = "p256dh", nullable = false, length = 120)
    private String p256dh;

    /** Client auth secret, base64url. */
    @Column(name = "auth", nullable = false, length = 120)
    private String auth;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    /** Last successful send; a subscription that stops answering is deleted. */
    @Column(name = "last_seen_at")
    private Timestamp lastSeenAt;
}
