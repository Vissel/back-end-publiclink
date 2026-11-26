package com.qrpublic.apartment.badminton.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Weekly schedule in the seller court response. */
@Data
@Builder
public class BadmintonCourtScheduleResponse {
    private int dayOfWeek;
    private String openTime;
    private String closeTime;
    private int slotMinutes;
    private BigDecimal priceVnd;
}
