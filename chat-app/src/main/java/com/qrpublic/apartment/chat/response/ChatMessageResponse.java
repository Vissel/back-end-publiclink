package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat message as seen by both sides of the bridge. The same shape is returned
 * by
 * REST calls and pushed over the server-sent-event stream, so the client can
 * render
 * a message from either source without branching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private Long id;

    private String conversationUuid;

    /** CLIENT or ADMIN. */
    private String direction;

    private String content;

    /** PENDING / SENT / DELIVERED / SEEN / REPLIED / FAILED. */
    private String status;

    private String clientMessageId;

    /** ISO-8601 timestamp of when the message was written. */
    private String createdAt;

    /** ISO-8601 timestamp of when Zalo confirmed delivery to the admin device. */
    private String deliveredAt;

    /** ISO-8601 timestamp of when the admin opened the Zalo chat. */
    private String seenAt;

    private Integer errorCode;

    /** Short customer label the admin sees in Zalo, e.g. 0042. */
    private String clientTag;
}
