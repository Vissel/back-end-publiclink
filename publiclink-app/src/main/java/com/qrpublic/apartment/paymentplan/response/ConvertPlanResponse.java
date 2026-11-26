package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * A conversion order that has been opened: the difference the seller now has to
 * transfer, and what their plan becomes once it is paid. Returned inside
 * {@code PaymentPlanService}, not on the wire; the screen reads
 * {@link MorePlanStateResponse.ConversionOutcome} instead.
 */
@Data
public class ConvertPlanResponse {

    /** the assignment being converted */
    private String sourcePlanUserCode;

    /** the order row opened for the difference */
    private String conversionPlanUserCode;

    private String targetPlanCode;
    private String targetPlanGroup;
    private BigDecimal targetFee;
    private int targetPlanDay;
    private int targetEnvCount;

    /** what is due now: target fee minus the fee already paid */
    private BigDecimal feeDelta;

    /** the converted plan's new window, projected at the time of the order */
    private String newActiveDate;
    private String newEndedDate;

    private String paymentCode;
    private String paymentUrl;
    private String status;
    private String sellerUsername;
}
