package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typing signal from either side. Only the administrator console can originate
 * it
 * for the admin — Zalo exposes no typing indicator, so a reply written in the
 * Zalo
 * app arrives as a message without a preceding hint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatTypingRequest {

    private String conversationUuid;

    private String deviceId;

    private Boolean typing;
}
