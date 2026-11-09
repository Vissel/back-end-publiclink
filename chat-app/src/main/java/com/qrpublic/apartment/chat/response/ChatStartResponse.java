package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of opening or resuming a chat: which device the visitor is, which
 * conversation continues, and the history to render immediately.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStartResponse {

    /** The id the widget must keep in local storage for the next visit. */
    private String deviceId;

    private ChatConversationResponse conversation;

    /** NEW / DEVICE_ID / FINGERPRINT / IP. */
    private String recognition;

    /** True when an earlier thread was picked up instead of started fresh. */
    private boolean resumed;

    /** How many times this device has been seen, first visit included. */
    private Integer visitCount;

    private List<ChatMessageResponse> messages;

    /** Server time, so the client can render relative timestamps consistently. */
    private String serverTime;
}
