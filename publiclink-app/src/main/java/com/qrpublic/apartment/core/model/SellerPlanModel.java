package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Seller-defined buyer payment plan (days mapped to a VND amount) linked to
 * one sale environment. Cross-layer identity is sellerPlanCode / username,
 * never the DB id.
 */
@Data
@Builder
public class SellerPlanModel {
    private String sellerPlanCode;
    private String envId;
    private String sellerUsername;
    private String buyerName;
    private String buyerUsername;
    private String buyerEmail;
    private int totalDays;
    private int usedDays;
    private int remainingDays;
    private BigDecimal amountVnd;
    private String state;
    private String createdAt;
    /** password setup link; only filled where the UI may show/copy it */
    private String setupLink;
    private boolean passwordSet;
}
