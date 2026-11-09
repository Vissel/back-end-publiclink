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
}
