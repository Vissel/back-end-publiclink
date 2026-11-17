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
    private java.math.BigDecimal amountVnd;
    private String state;
}
