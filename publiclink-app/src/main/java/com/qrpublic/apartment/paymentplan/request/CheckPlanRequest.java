package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Quota gate asked by the Generate Product Link flow before moving to screen 2.
 */
@Data
public class CheckPlanRequest {
    private String username;
    /**
     * STANDARD | YOGA_BOOKING | BADMINTON_COURT. When present the gate only
     * counts plans of the group that product type belongs to, so the answer
     * says whether this link can actually be paid for. Blank keeps the gate
     * group-agnostic, which is what the workspace banner needs.
     */
    private String productType;
}
