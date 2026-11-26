package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

/**
 * Answer of the quota gate: whether the seller may still create environments
 * and how many are left.
 */
@Data
public class CheckPlanResponse {
    private boolean allowed;
    private int remaining;
    private String message;
    /** OK | NO_PLAN | NOT_STARTED | EXPIRED | QUOTA_EXHAUSTED | WRONG_GROUP */
    private String reason;
    /**
     * The group the gate was asked for, and the one the usable plan belongs to.
     * Only set when a product type was given, so a wrong-group refusal can name
     * the plan group the seller actually has to buy.
     */
    private String planGroup;
    private String activeDate;
    /** paid plan end; a generated link is clamped to this instant */
    private String endedDate;
    /**
     * Latest paid moment whatever plan it comes from, quota ignored. The bound
     * for extending an already created sale window, which uses no new slot.
     */
    private String coverageEndedDate;
}
