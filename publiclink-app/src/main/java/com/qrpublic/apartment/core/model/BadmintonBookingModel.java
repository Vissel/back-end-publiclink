package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Public badminton booking contract. Times are HH:mm and date is yyyy-MM-dd.
 */
@Data
@Builder
public class BadmintonBookingModel {
    private String bookingCode;
    private String courtCode;
    private String courtName;
    private String date;
    private String startTime;
    private String endTime;
    private BigDecimal priceVnd;
    private String status;
    private String buyerUsername;
    private String buyerName;
    private String note;
}
