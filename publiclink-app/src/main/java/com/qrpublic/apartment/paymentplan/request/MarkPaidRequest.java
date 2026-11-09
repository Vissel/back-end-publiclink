package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Admin confirms the money really arrived: payment -> PAID and a bill record
 * is created from it.
 */
@Data
public class MarkPaidRequest {
    private String paymentCode;
    private String note;
}
