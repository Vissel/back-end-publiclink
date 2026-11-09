package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "payment_plan")
@Getter
@Setter
@NoArgsConstructor
public class PaymentPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "plan_code", nullable = false, unique = true, length = 36)
    private String planCode;

    @Column(name = "fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(name = "plan_day", nullable = false)
    private int planDay;

    @Column(name = "env_count", nullable = false)
    private int envCount = 1;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "plan_state", nullable = false, length = 50)
    private String planState = "ACTIVE";

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
