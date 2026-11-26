package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Change which plan group an account is offered. Exists because a conversion
 * never crosses groups, so the only way out of a purpose picked wrongly at
 * registration is to say so explicitly. The caller comes from the
 * gateway-forwarded header, never from here.
 */
@Data
public class ChangePlanPurposeRequest {

    /** PRODUCT or BOOKING; anything else is read as PRODUCT */
    private String planPurpose;
}
