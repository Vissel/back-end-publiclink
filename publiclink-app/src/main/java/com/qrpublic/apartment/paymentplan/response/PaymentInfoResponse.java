package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the public (no authorization) payment page shows: plan detail,
 * estimated dates, and the admin transfer accounts to pay into.
 */
@Data
public class PaymentInfoResponse {

    private String paymentCode;
    private BigDecimal fee;
    private int planDay;
    private int envCount;
    private String description;
    private BigDecimal amount;
    private String currency;
    /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
    private String status;
    private String estimatedActiveDate;
    private String estimatedEndDate;
    private List<TransferItem> transferInfos;

    @Data
    public static class TransferItem {
        private String transferIssuer;
        private String issuerFullName;
        private String type;
        private String transferNumber;
        private String accountName;
        private String qrImage;
        private String defaultMessage;
        private boolean isDefault;
    }
}
