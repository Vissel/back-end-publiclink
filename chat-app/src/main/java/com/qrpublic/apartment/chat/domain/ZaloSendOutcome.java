package com.qrpublic.apartment.chat.domain;

import lombok.Data;

/**
 * Result of one Zalo dispatch attempt, kept deliberately non-exceptional so the
 * chat flow can continue when Zalo refuses a message.
 */
@Data
public class ZaloSendOutcome {

    private boolean delivered;

    private String zaloMessageId;

    /** Zalo error code when refused, HTTP/transport failure otherwise. */
    private Integer errorCode;

    private String reason;

    public static ZaloSendOutcome delivered(String zaloMessageId) {
        ZaloSendOutcome outcome = new ZaloSendOutcome();
        outcome.setDelivered(true);
        outcome.setZaloMessageId(zaloMessageId);
        return outcome;
    }

    public static ZaloSendOutcome failed(Integer errorCode, String reason) {
        ZaloSendOutcome outcome = new ZaloSendOutcome();
        outcome.setDelivered(false);
        outcome.setErrorCode(errorCode);
        outcome.setReason(reason);
        return outcome;
    }
}
