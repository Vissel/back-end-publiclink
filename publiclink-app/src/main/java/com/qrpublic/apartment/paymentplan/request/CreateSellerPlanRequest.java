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

    /**
     * Start the seller asked for, yyyy-MM-dd'T'HH:mm. Optional; the admin
     * assignment flow leaves it empty and keeps starting on activation day.
     */
    private String startAt;
}
