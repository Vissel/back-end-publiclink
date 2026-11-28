package com.qrpublic.apartment.chat.core.model;

/**
 * IMAGE renders inline as a thumbnail with open/download; FILE renders as a
 * download row. Anything else is refused at upload time.
 */
public enum ChatAttachmentKind {
    IMAGE,
    FILE
}
