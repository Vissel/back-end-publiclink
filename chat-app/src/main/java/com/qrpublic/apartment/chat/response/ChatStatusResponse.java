package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Narrow payload of a status transition frame (delivered / seen / replied...).
 *
 * Deliberately NOT a full {@link ChatMessageResponse}: status transitions are
 * loaded from the message row alone, without the attachment join, so a full
 * payload would carry {@code attachment: null} and every client merging the
 * frame over its current bubble would erase the image or file it already
 * renders - which is exactly what made live attachment messages fall back to
 * their placeholder text until a page refresh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStatusResponse {

    private String conversationUuid;

    private Long id;

    /** Lets a client match the frame to an optimistic bubble as well. */
    private String clientMessageId;

    /** PENDING / SENT / DELIVERED / SEEN / REPLIED / FAILED. */
    private String status;

    private String deliveredAt;

    private String seenAt;

    private Integer errorCode;
}
