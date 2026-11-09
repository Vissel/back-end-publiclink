package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Quota gate asked by the Generate Product Link flow before moving to screen 2.
 */
@Data
public class CheckPlanRequest {
    private String username;
}
