package com.qrpublic.apartment.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * One chat thread owned by a client device. The same device resumes the same
 * conversation every time it comes back, so history is never lost between
 * visits.
 */
@Entity
@Table(name = "chat_conversation")
@Getter
@Setter
@NoArgsConstructor
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 64)
    private String uuid;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** ACTIVE / IDLE / CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Short label rendered into the Zalo message so the admin can tag the customer.
     */
    @Column(name = "client_tag", length = 20)
    private String clientTag;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "last_message_at")
    private Timestamp lastMessageAt;

    /**
     * Guest messages the administrator has not opened yet. Incremented on every
     * client message, cleared when the console loads the thread (changeset-002).
     */
    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    /**
     * Signed-in account this thread belongs to, or null for an anonymous
     * visitor. Account threads are resolved by this column alone: two accounts
     * on one browser share a device id, so device identity must never merge
     * them (changeset-003).
     */
    @Column(name = "owner_username", length = 64)
    private String ownerUsername;

    /** Human name of the owner, resolved from the user service; cosmetic only. */
    @Column(name = "owner_display_name", length = 120)
    private String ownerDisplayName;

    /**
     * Capability secret for the visitor's event stream. EventSource cannot send
     * a JWT and the device id is shared between accounts on one browser, so the
     * live feed is keyed by this per-conversation token instead.
     */
    @Column(name = "stream_key", length = 64)
    private String streamKey;
}
