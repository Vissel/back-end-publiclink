package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Liveness payload of the chat service. Deliberately minimal — this endpoint is
 * public, so traffic numbers and identifiers stay out of it; the authenticated
 * console reports those.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHealthResponse {

    /** Always "UP" once the context is serving requests. */
    private String status;

    private String service;

    /** Public namespace this service answers under, for routing diagnostics. */
    private String namespace;

    /** Whether the Zalo leg is switched on, or the chat is console-only. */
    private boolean zaloEnabled;
}
