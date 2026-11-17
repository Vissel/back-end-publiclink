package com.qrpublic.apartment.model.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private String targetType;
    private String requestUuid;
    private boolean isRead;
    private String createdAt;
    private String senderName;
    private String senderEmail;

    /**
     * What the notification is about: null for a plain message, PAYMENT for the
     * "Payment confirmation received" rows. {@code referenceCode} is then the
     * payment code behind {@code payment}.
     */
    private String referenceType;
    private String referenceCode;

    /**
     * Structured body of a PAYMENT notification. Only filled in the detail
     * response, where the admin console renders it instead of the plain text
     * and offers the "Mark paid" action.
     */
    private PaymentRef payment;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRef {
        private String paymentCode;
        private String planUserCode;
        private String sellerUsername;
        private String amount;
        private String currency;
        /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
        private String status;
        private String sellerEmail;
        private String sellerPhone;
        private String planDescription;
        private String paidAt;
        /** days bought when this payment extends a running plan */
        private Integer extendDays;
    }
}
