package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Core layer view of a payment_plan_user assignment (one user, one plan).
 * External identifier is planUserCode; never exposes DB ids.
 */
@Data
@Builder
public class PlanUserModel {
    private String planUserCode;
    private String username;
    private String displayName;
    private BigDecimal fee;
    private int planDay;
    private int envQuota;
    private int usedCount;
    private int remaining;
    /** CREATE | ACTIVE | DEACTIVE */
    private String planState;
    private String activeDate;
    private String endedDate;
    private String createdAt;
    private String paymentCode;
    /** payment status of the linked payment record */
    private String paymentStatus;
    /** username of the admin who created the assignment */
    private String createdBy;
}
