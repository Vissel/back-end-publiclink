package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Self-service plan pick: the seller buys one catalog plan for themselves, so
 * only the plan code travels. Identity comes from the gateway header, never
 * from the body.
 */
@Data
public class SelectPlanRequest {

    private String planCode;
}
