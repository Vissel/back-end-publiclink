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
}
