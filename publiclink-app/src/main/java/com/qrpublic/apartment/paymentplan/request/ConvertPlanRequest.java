package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Ask to move a running plan onto a more expensive plan of the same purpose
 * group, paying only the difference. The caller is taken from the
 * gateway-forwarded header, never from here.
 */
@Data
public class ConvertPlanRequest {

    /** the seller's own ACTIVE assignment being converted */
    private String planUserCode;

    /** catalog plan code to convert to */
    private String targetPlanCode;
}
