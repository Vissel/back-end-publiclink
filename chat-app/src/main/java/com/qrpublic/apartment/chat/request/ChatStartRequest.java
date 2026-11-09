package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Opens or resumes a chat conversation for a visitor.
 *
 * {@code deviceId} is the identifier the widget keeps in local storage; when it
 * is
 * missing (cleared storage, new browser profile) the server falls back to the
 * attribute fingerprint and hashed IP. The raw attributes are hashed
 * server-side
 * and never persisted as sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStartRequest {

    private String deviceId;

    /**
     * Ordered browser attributes; the position of each value is the identity of the
     * attribute.
     */
    private List<String> attributes;

    /** Optional page context, e.g. the sale environment the visitor came from. */
    private String context;
}
