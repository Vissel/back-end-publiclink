package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "payment_plan_user")
@Getter
@Setter
@NoArgsConstructor
public class PaymentPlanUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "plan_user_code", nullable = false, unique = true, length = 36)
    private String planUserCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private PaymentPlan plan;

    @Column(name = "env_quota", nullable = false)
    private int envQuota = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "plan_state", nullable = false, length = 20)
    private String planState = "CREATE";

    @Column(name = "active_date")
    private Timestamp activeDate;

    @Column(name = "ended_date")
    private Timestamp endedDate;

    /**
     * Days bought on top of an already running plan. Non-null marks this row as
     * an extension order: it grants no environment slot of its own, and paying
     * it grows {@code extendsPlanUserCode}'s end date instead.
     */
    @Column(name = "extend_days")
    private Integer extendDays;

    /**
     * plan_user_code of the assignment this extension adds its days to.
     */
    @Column(name = "extends_plan_user_code", length = 36)
    private String extendsPlanUserCode;

    /**
     * plan_user_code of the assignment this order replaces. Non-null marks the
     * row as a conversion order: it grants no environment slot of its own, and
     * paying it swaps the converted plan for the one this row points at.
     */
    @Column(name = "converts_plan_user_code", length = 36)
    private String convertsPlanUserCode;

    /**
     * Money actually due for a conversion order: the target plan's fee minus
     * what the converted plan cost. Stored so the queue tables and the payment
     * both read the difference instead of the full target price.
     */
    @Column(name = "convert_fee_delta", precision = 15, scale = 2)
    private BigDecimal convertFeeDelta;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
