package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Seller create/update of a buyer payment plan for a yoga environment. */
@Data
public class SellerPlanUpsertRequest {
    private String token;
    /** null on create, set on update */
    private String sellerPlanCode;
    private String buyerName;
    private String buyerEmail;
    private Integer totalDays;
    /** DAYS (one unit per calendar date) or BOOKINGS (one unit per booking) */
    private String quotaType;
    private java.math.BigDecimal amountVnd;
    private String state;
}
