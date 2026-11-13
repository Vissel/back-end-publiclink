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
    /** OK | NO_PLAN | NOT_STARTED | EXPIRED | QUOTA_EXHAUSTED */
    private String reason;
    private String activeDate;
    /** paid plan end; a generated link is clamped to this instant */
    private String endedDate;
}
