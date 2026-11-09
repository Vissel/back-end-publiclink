package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Multi-select reject / deactivate: -> DEACTIVE.
 */
@Data
public class RejectPlanRequest {
    private List<String> planUserCodes;
}
