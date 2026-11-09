package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "bill")
@Getter
@Setter
@NoArgsConstructor
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "bill_code", nullable = false, unique = true, length = 36)
    private String billCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "billed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal billedAmount;

    @Column(name = "note", length = 250)
    private String note;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;
}
