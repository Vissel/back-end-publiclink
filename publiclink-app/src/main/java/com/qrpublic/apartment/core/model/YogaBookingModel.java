package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * One yoga booking above the core layer.
 */
@Data
@Builder
public class YogaBookingModel {
    private String bookingCode;
    private String classCode;
    private String title;
    private String coach;
    private String date;
    private String startTime;
    private String endTime;
    private String buyerUsername;
    private String buyerName;
    /** DB id of the consumed seller plan row; used only for day refunds */
    private Long sellerPlanId;
    private String paymentMode;
    private String status;
    private boolean paid;
    private boolean attended;
    private String bookedBy;
    private String note;
}
