package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The catalog plan that was just created. No payment and no seller: linking is
 * a separate action, so this only describes the plan itself.
 */
@Data
public class CreatedPlanResponse {
    private String planCode;
    private BigDecimal fee;
    private int planDay;
    private int envCount;
    private String description;
    private String planState;
}
