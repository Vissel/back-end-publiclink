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

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * One buyer slot reservation on a concrete date of a weekly yoga occurrence.
 * Account buyers carry buyer_username (unique per class+date); anonymous
 * pay-as-you-go bookings only carry buyer_name. seller_plan_id is set when the
 * booking consumed a day from a seller payment plan (no FK by design, see
 * changeset 026/027 notes).
 */
@Entity
@Table(name = "yoga_booking")
@Getter
@Setter
@NoArgsConstructor
public class YogaBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "booking_code", nullable = false, unique = true, length = 36)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private YogaClass yogaClass;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "buyer_username", length = 50)
    private String buyerUsername;

    @Column(name = "buyer_name", length = 100)
    private String buyerName;

    @Column(name = "seller_plan_id")
    private Long sellerPlanId;

    @Column(name = "payment_mode", nullable = false, length = 20)
    private String paymentMode;

    @Column(nullable = false, length = 20)
    private String status = "BOOKED";

    @Column(nullable = false)
    private boolean paid;

    @Column(nullable = false)
    private boolean attended;

    @Column(name = "booked_by", length = 50)
    private String bookedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
