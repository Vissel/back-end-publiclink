package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The extension order that was just opened: what it costs, which window it buys
 * and the payment link to hand to the seller. The plan's own end date only
 * moves once the payment behind {@code paymentCode} is confirmed.
 */
@Data
public class ExtendPlanResponse {

    /** the ACTIVE plan being extended */
    private String planUserCode;
    /** the queue row / assignment created for this order */
    private String extensionPlanUserCode;
    private String sellerUsername;
    private int days;
    /** pro-rated price of the bought days */
    private BigDecimal fee;
    private int planDay;
    /** end date the plan has right now */
    private String currentEndDate;
    /** end date once this order is paid */
    private String extendedEndDate;
    private String paymentCode;
    private String paymentUrl;
    /** payment status, CREATE until the seller confirms */
    private String status;
}
