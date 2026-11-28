package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

/**
 * The window a plan would run for, calculated without reserving anything.
 * Both dates are yyyy-MM-dd'T'HH:mm in the same time zone the request used.
 */
@Data
public class PlanWindowResponse {

    private String startAt;

    /** start + the plan's own length; the seller never edits this */
    private String endAt;

    /** days the plan buys, repeated so the screen can say it without the catalog */
    private int planDay;
}