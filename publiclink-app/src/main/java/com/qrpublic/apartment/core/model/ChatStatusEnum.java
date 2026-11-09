package com.qrpublic.apartment.core.model;

/**
 * Delivery ladder of a chat message, shown as status icons in the app chat.
 *
 * Zalo owns SENT / DELIVERED / SEEN for the admin leg: SENT comes from the OA
 * message API acknowledgement, DELIVERED from the user_received_message webhook
 * and SEEN from user_seen_message (fired when the admin opens the Zalo chat).
 * REPLIED is set on a client message once the admin answer is routed back.
 *
 * The ladder only moves forward — a delayed or replayed webhook (Zalo retries up
 * to five times) must never downgrade an already-confirmed status, which is what
 * {@link #isAtOrAfter(ChatStatusEnum)} guards.
 */
public enum ChatStatusEnum {
    /** Persisted, dispatch to Zalo not acknowledged yet. */
    PENDING(0),
    /** Accepted by the Zalo OpenAPI (message id known). */
    SENT(1),
    /** Reached the administrator's Zalo device. */
    DELIVERED(2),
    /** Administrator opened the Zalo chat containing the message. */
    SEEN(3),
    /** Administrator answered this message. */
    REPLIED(4),
    /** Dispatch failed and retries are exhausted. */
    FAILED(9);

    private final int rank;

    ChatStatusEnum(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /** True when this status is at or beyond {@code other} on the ladder. */
    public boolean isAtOrAfter(ChatStatusEnum other) {
        if (other == null) {
            return true;
        }
        if (this == FAILED || other == FAILED) {
            return this == other;
        }
        return this.rank >= other.rank;
    }

    /** Lenient parse used when reading status strings coming from the database. */
    public static ChatStatusEnum from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return ChatStatusEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
