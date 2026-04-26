package com.qrpublic.apartment.util;

import com.qrpublic.apartment.constant.CommonConstant;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Utils {
    public static String subDate(Timestamp createdAt) {
        // "2025-06-12T19:26:03.000+00:00"

        return createdAt.toString().substring(0, createdAt.toString().indexOf(CommonConstant.DOT))
                .replace(CommonConstant.T_STR, CommonConstant.SPACE);
    }

    public static String formatTimeStamp(Timestamp timestamp) {
        if (timestamp == null) {
            return CommonConstant.EMPTY;
        }
        Instant instant = timestamp.toInstant();
        ZoneId zoneId = ZoneId.of("UTC");
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return zonedDateTime.format(formatter);
    }


    public static boolean isValidStr(String string) {
        return string != null && !string.isBlank();
    }
}
