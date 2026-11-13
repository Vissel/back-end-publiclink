package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/**
 * Calendar week request. weekStart is a Monday yyyy-MM-dd; blank means the
 * current week (server DB timezone).
 */
@Data
public class YogaCalendarRequest {
    private String token;
    private String weekStart;
}
