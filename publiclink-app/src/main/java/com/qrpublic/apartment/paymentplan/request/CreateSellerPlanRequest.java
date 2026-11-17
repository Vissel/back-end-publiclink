package com.qrpublic.apartment.paymentplan.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Links an existing catalog plan to one seller, which is what starts the
 * payment flow. The plan itself is created separately and independently of any
 * user, so only the plan code travels here together with the seller username.
 */
@Data
public class CreateSellerPlanRequest {

    @NotNull
    private String username;

    private String planCode;
}
