package com.qrpublic.apartment.account.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One payment package a seller issued to this buyer, shown on the buyer's own
 * /self page. The unit the quota counts in travels with it, so the page never
 * has to guess whether a number means days or bookings.
 */
@Data
@Builder
public class BuyerPackageResponse {
    private String sellerPlanCode;
    private String envId;
    /** the seller page this package pays for; best-effort label */
    private String productName;
    private String sellerUsername;
    private String buyerName;
    /** DAYS or BOOKINGS */
    private String quotaType;
    private int totalDays;
    private int usedDays;
    private int usedBookings;
    private int remainingDays;
    private BigDecimal amountVnd;
    private String state;
}
