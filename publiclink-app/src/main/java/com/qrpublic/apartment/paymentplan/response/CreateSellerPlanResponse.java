package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Result of creating a seller plan assignment: the payment URL the admin
 * copies and sends to the seller manually.
 */
@Data
public class CreateSellerPlanResponse {
    private String paymentCode;
    /** relative URL, e.g. /payment?token=... */
    private String paymentUrl;
    private String sellerUsername;
    private BigDecimal fee;
    private int planDay;
    private int envCount;
    private String status;
}
