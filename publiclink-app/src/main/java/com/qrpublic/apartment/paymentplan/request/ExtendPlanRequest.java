package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Buy extra days on a plan assignment that is already running. The order is
 * priced at the plan's daily rate and has to be paid like any other payment,
 * so it lands in the "Waiting for pay" queue until the admin confirms it.
 */
@Data
public class ExtendPlanRequest {

    /** the ACTIVE assignment to extend */
    private String planUserCode;

    /** days to add to the plan's end date */
    private int days;
}
