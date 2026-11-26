package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request from another service to append a system-generated message to
 * a signed-in player's chat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSystemMessageRequest {

    private String ownerUsername;

    private String content;
}
