package com.qrpublic.apartment.badminton.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One public grid cell. */
@Data
@Builder
public class BadmintonFrameResponse {
    private String startTime;
    private String endTime;
    private BigDecimal priceVnd;
    private String state;
    private String bookingCode;
}
