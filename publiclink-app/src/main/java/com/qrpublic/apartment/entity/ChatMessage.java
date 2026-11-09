package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * A single chat message. Direction CLIENT is written by the web visitor and
 * mirrored into Zalo; direction ADMIN is the administrator's answer.
 *
 * Status tracks the delivery ladder shown in the app chat:
 * PENDING -> SENT -> DELIVERED -> SEEN -> REPLIED (or FAILED).
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    /** Client-generated id used to de-duplicate a retried POST. */
    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    /** CLIENT / ADMIN */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Message id returned by Zalo — the key used to match receipt/seen/quote events. */
    @Column(name = "zalo_message_id", length = 64)
    private String zaloMessageId;

    /** Local message id this reply quotes (Zalo quote-reply routing). */
    @Column(name = "quote_message_id")
    private Long quoteMessageId;

    @Column(name = "error_code")
    private Integer errorCode;

    /** Insert-only: the value is written by the application and stays readable
     *  immediately after save (an insertable=false column would come back null). */
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "delivered_at")
    private Timestamp deliveredAt;

    @Column(name = "seen_at")
    private Timestamp seenAt;
}
