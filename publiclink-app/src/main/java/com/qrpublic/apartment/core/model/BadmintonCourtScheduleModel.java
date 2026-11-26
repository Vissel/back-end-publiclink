package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Weekly schedule values exchanged between the core and domain layers. */
@Data
@Builder
public class BadmintonCourtScheduleModel {
    private int dayOfWeek;
    private String openTime;
    private String closeTime;
    private int slotMinutes;
    private BigDecimal priceVnd;
}
