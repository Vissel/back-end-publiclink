package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Core layer view of a payment record. External identifier is paymentCode.
 */
@Data
@Builder
public class PaymentModel {
    private String paymentCode;
    private String planUserCode;
    private String username;
    private BigDecimal amount;
    private String currency;
    /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
    private String status;
    private String paymentUrl;
    /** VietQR Quick Link image URL for the seller payment page */
    private String qrUrl;
    private String qrAddInfo;
    /** admin who created the payment */
    private String createdBy;
    private String sellerEmail;
    private String sellerPhone;
    private String confirmedAt;
    private String paidAt;
    private String createdAt;
    private PaymentPlanModel plan;
}
