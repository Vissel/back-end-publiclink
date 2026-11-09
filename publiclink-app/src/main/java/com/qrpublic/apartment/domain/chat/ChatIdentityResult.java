package com.qrpublic.apartment.domain.chat;

import com.qrpublic.apartment.core.model.ChatConversationModel;
import com.qrpublic.apartment.core.model.ChatDeviceModel;
import lombok.Data;

/**
 * Outcome of identifying a visitor: which stored device this is, which
 * conversation the visit continues, and how confident the match was.
 */
@Data
public class ChatIdentityResult {

    private ChatDeviceModel device;

    private ChatConversationModel conversation;

    /** NEW / DEVICE_ID / FINGERPRINT / IP. */
    private String recognition;

    /** True when an earlier conversation was resumed instead of opened anew. */
    private boolean resumed;

    /** Similarity of the winning match, for diagnostics only. */
    private double confidence;
}
