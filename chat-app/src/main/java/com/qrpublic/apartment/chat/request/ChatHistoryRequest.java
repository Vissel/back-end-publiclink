package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reads conversation history, optionally only what arrived after a given
 * message —
 * the call a client makes when it reconnects after losing the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryRequest {

    private String conversationUuid;

    /** Exclusive lower bound; null means the whole thread. */
    private Long afterMessageId;

    /** Client-side page limit, clamped by the service. */
    private Integer limit;
}
