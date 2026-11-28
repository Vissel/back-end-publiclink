package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Admin confirms the money really arrived for every selected assignment:
 * each linked payment goes to PAID and a bill record is created from it, and
 * an assignment that is still in CREATE starts running from {@code startAt}.
 * Selections travel as plan user codes, the same identifier the setup tables
 * and the approve / reject actions use.
 */
@Data
public class MarkPaidRequest {
    private List<String> planUserCodes;
    private String note;
    /**
     * yyyy-MM-dd'T'HH:mm; any value is an explicit admin choice, blank means
     * "use the start the seller asked for, or today".
     */
    private String startAt;
}