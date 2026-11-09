package com.qrpublic.apartment.paymentplan.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Admin creates a payment plan assignment for one seller.
 * Either an existing catalog plan (planCode) or an inline new plan definition
 * is provided. Only usernames travel on the wire, never DB ids.
 */
@Data
public class CreateSellerPlanRequest {

    @NotNull
    private String username;

    private String planCode;

    private NewPlan newPlan;

    @Data
    public static class NewPlan {
        private BigDecimal fee;
        private int planDay;
        private int envCount = 1;
        private String description;
    }
}
