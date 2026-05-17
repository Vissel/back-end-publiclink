package com.qrpublic.apartment.util;

import com.qrpublic.apartment.constant.CommonConstant;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtils {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss").withZone(ZoneOffset.UTC);
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String dateToString(Date date) {
        if (date == null) {
            return CommonConstant.EMPTY; // Or return "" based on your preference
        }
        return simpleDateFormat.format(date);
    }

    public static String dateToString(Timestamp dateTime) {
        if (dateTime == null) {
            return CommonConstant.EMPTY; // Or return "" based on your preference
        }
        return simpleDateFormat.format(dateTime);
    }
}
