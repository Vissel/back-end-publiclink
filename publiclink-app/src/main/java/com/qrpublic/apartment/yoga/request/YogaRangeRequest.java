package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/**
 * Date-range query below the calendar and for the seller booking list.
 * Dates are yyyy-MM-dd.
 */
@Data
public class YogaRangeRequest {
    private String token;
    private String fromDate;
    private String toDate;
}
