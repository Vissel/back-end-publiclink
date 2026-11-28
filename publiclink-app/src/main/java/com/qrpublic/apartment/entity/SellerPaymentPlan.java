package com.qrpublic.apartment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Payment plan defined by the seller for one buyer of a yoga environment:
 * buyer name and a quota mapped to a VND amount, linked to the sale
 * environment the seller created. {@code quotaType} says what the quota counts:
 * DAYS consumes one unit per calendar date (any number of slots in it),
 * BOOKINGS one unit per booking. {@code usedDays} and {@code usedBookings}
 * always carry both counts so neither number is ever presented as the other.
 */
@Entity
@Table(name = "seller_payment_plan")
@Getter
@Setter
@NoArgsConstructor
public class SellerPaymentPlan {

    public static final String QUOTA_DAYS = "DAYS";
    public static final String QUOTA_BOOKINGS = "BOOKINGS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_plan_id")
    private Long sellerPlanId;

    @Column(name = "seller_plan_code", nullable = false, unique = true, length = 36)
    private String sellerPlanCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "env_id", nullable = false)
    private SaleEnvironment saleEnvironment;

    @Column(name = "seller_username", nullable = false, length = 50)
    private String sellerUsername;

    @Column(name = "buyer_name", nullable = false, length = 100)
    private String buyerName;

    @Column(name = "buyer_username", nullable = false, unique = false, length = 50)
    private String buyerUsername;

    @Column(name = "buyer_email", length = 150)
    private String buyerEmail;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "used_days", nullable = false)
    private int usedDays;

    @Column(name = "used_bookings", nullable = false)
    private int usedBookings;

    @Column(name = "quota_type", nullable = false, length = 10)
    private String quotaType = QUOTA_DAYS;

    @Column(name = "amount_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountVnd;

    @Column(nullable = false, length = 20)
    private String state = "ACTIVE";

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
