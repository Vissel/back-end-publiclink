package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_code", nullable = false, unique = true, length = 36)
    private String paymentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_user_id", nullable = false)
    private PaymentPlanUser planUser;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "VND";

    /**
     * CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "CREATE";

    @Column(name = "payment_url", length = 500)
    private String paymentUrl;

    /**
     * VietQR Quick Link image URL generated for this payment, linked to the
     * payment code; the seller payment page renders it directly.
     */
    @Column(name = "qr_url", length = 1000)
    private String qrUrl;

    /**
     * Transfer content (username + plan) baked into the QR addInfo.
     */
    @Column(name = "qr_add_info", length = 100)
    private String qrAddInfo;

    /**
     * Admin username that created this payment (who sent the plan to seller).
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "seller_email", length = 100)
    private String sellerEmail;

    @Column(name = "seller_phone", length = 20)
    private String sellerPhone;

    @Column(name = "confirmed_at")
    private Timestamp confirmedAt;

    @Column(name = "paid_at")
    private Timestamp paidAt;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
