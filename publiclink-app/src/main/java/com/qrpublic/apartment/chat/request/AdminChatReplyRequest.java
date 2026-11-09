package com.qrpublic.apartment.chat.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An answer typed by the administrator in the console (Mode B). When the Zalo
 * bridge is enabled the same text is mirrored into the Zalo thread so the
 * administrator's Zalo history stays truthful about what was answered where.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatReplyRequest {

    private String conversationUuid;

    private String content;
}
