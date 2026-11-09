package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
