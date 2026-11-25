package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One generated court frame before booking state is overlaid by the domain. */
@Data
@Builder
public class BadmintonFrameModel {
    public static final String FREE = "FREE";
    public static final String BOOKED = "BOOKED";
    public static final String MINE = "MINE";

    private String startTime;
    private String endTime;
    private BigDecimal priceVnd;
    private String state;
    private String bookingCode;
}
