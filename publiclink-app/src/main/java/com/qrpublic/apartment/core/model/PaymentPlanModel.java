package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Core layer view of a payment plan catalog entry. Carries planCode (external
 * identifier) instead of the DB id.
 */
@Data
@Builder
public class PaymentPlanModel {
    private String planCode;
    private BigDecimal fee;
    private int planDay;
    private int envCount;
    private String description;
    private String planState;
}
