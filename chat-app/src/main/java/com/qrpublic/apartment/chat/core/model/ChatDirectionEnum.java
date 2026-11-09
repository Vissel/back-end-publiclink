package com.qrpublic.apartment.chat.core.model;

/**
 * Who authored the chat message.
 */
public enum ChatDirectionEnum {
    /** Written by the client in the app chat, mirrored out to Zalo. */
    CLIENT,
    /** Written by the administrator (in Zalo or in the admin console). */
    ADMIN
}
