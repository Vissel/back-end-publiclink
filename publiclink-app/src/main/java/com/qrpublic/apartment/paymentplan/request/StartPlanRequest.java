package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Admin moves the start of one or more plan assignments, whether they are still
 * waiting for payment or already running. The end is always recomputed from the
 * plan length, so this request carries the start only.
 */
@Data
public class StartPlanRequest {
    private List<String> planUserCodes;
    /** yyyy-MM-dd'T'HH:mm, the same wire format the plan screens use */
    private String startAt;
}