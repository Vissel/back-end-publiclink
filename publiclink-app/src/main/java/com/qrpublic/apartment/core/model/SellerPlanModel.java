package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Seller-defined buyer payment plan linked to one sale environment. The quota
 * is measured in the unit named by {@code quotaType}: DAYS (one day per
 * calendar date, any number of slots in it) or BOOKINGS (one unit per booking).
 * Cross-layer identity is sellerPlanCode / username, never the DB id.
 */
@Data
@Builder
public class SellerPlanModel {
    public static final String QUOTA_DAYS = "DAYS";
    public static final String QUOTA_BOOKINGS = "BOOKINGS";

    private String sellerPlanCode;
    private String envId;
    private String sellerUsername;
    private String buyerName;
    private String buyerUsername;
    private String buyerEmail;
    /** quota total, counted in the unit quotaType names */
    private int totalDays;
    /** distinct calendar dates consumed */
    private int usedDays;
    /** bookings made */
    private int usedBookings;
    /** quota left in the unit quotaType names */
    private int remainingDays;
    private String quotaType;
    private BigDecimal amountVnd;
    private String state;
    private String createdAt;
    /** password setup link; only filled where the UI may show/copy it */
    private String setupLink;
    private boolean passwordSet;

    public boolean countsBookings() {
        return QUOTA_BOOKINGS.equals(quotaType);
    }
}
