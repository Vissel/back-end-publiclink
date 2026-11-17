package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * Outcome of checking a seller against their paid plan window and quota.
 * Carries the absolute plan deadline so the caller can clamp a generated link
 * to it, plus a machine-readable reason for the UI.
 */
@Data
@Builder
public class PlanGateModel {

    /** OK | NO_PLAN | NOT_STARTED | EXPIRED | QUOTA_EXHAUSTED */
    public static final String REASON_OK = "OK";
    public static final String REASON_NO_PLAN = "NO_PLAN";
    public static final String REASON_NOT_STARTED = "NOT_STARTED";
    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED";

    private boolean allowed;
    private String reason;
    /** human readable explanation, already mentions contacting admin / buying */
    private String message;
    private int remaining;
    private String activeDate;
    private String endedDate;
    /** absolute plan end; a generated link must never outlive this */
    private Date deadline;
    /**
     * Latest paid moment across every ACTIVE plan, quota ignored. Creating a
     * link is bounded by {@link #deadline} (the plan the slot comes from),
     * while stretching an existing sale window is bounded by this: it may
     * legally reach a later plan the seller also paid for.
     */
    private String coverageEndedDate;
}
