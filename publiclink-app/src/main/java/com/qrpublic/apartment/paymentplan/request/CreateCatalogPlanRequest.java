package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Creates a payment plan in the catalog. Intentionally carries no user: a plan
 * exists on its own and is linked to a seller later, through the assign step.
 */
@Data
public class CreateCatalogPlanRequest {

    private BigDecimal fee;
    private int planDay;
    /** environments allowed at the same time */
    private int envCount = 1;
    private String description;
}
