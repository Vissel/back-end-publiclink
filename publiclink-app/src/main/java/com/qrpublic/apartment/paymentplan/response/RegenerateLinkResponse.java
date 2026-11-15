package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.util.List;

/**
 * Freshly generated payment URLs, one entry per selected assignment, so the
 * admin can copy each of them and send it to the right seller.
 */
@Data
public class RegenerateLinkResponse {

    private int processed;
    private List<PaymentLinkItem> items;

    @Data
    public static class PaymentLinkItem {
        private String planUserCode;
        private String username;
        private String paymentCode;
        /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
        private String paymentStatus;
        /** relative URL, e.g. /payment?token=... */
        private String paymentUrl;
    }
}
