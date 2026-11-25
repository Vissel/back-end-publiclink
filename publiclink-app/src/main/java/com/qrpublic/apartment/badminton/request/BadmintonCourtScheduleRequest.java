package com.qrpublic.apartment.badminton.request;

import lombok.Data;

import java.math.BigDecimal;

/** One weekday's opening hours, frame length and price. */
@Data
public class BadmintonCourtScheduleRequest {
    private int dayOfWeek;
    private String openTime;
    private String closeTime;
    private int slotMinutes;
    private BigDecimal priceVnd;
}
