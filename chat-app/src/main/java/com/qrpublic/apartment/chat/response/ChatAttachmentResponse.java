package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata of the one binary payload of a message, carried by both REST
 * responses and stream frames. The bytes are fetched separately from the
 * download endpoints, keyed by {@code uuid}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentResponse {

    private String uuid;

    /** IMAGE (inline thumbnail) / FILE (download row). */
    private String kind;

    private String fileName;

    private String mimeType;

    private Long sizeBytes;
}
