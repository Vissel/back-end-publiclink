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
    /** CREATE | ACTIVE | DEACTIVE | EXTENDED | CONVERTED */
    private String planState;
    /** PRODUCT or BOOKING: which kind of link this plan pays for */
    private String planGroup;
    private String activeDate;
    private String endedDate;
    /** start the seller asked for when buying; null when nobody asked */
    private String requestedStartAt;
    /** days bought on top of another plan; non-null marks an extension order */
    private Integer extendDays;
    /** plan user code whose end date the extension adds its days to */
    private String extendsPlanUserCode;
    /** plan user code this order replaces; non-null marks a conversion order */
    private String convertsPlanUserCode;
    /** amount due for a conversion order, the difference between the two plans */
    private BigDecimal convertFeeDelta;
    private String createdAt;
    private String paymentCode;
    /** payment status of the linked payment record */
    private String paymentStatus;
    /** payment URL of the linked payment record, relative to the app host */
    private String paymentUrl;
    /** username of the admin who created the assignment */
    private String createdBy;
}
