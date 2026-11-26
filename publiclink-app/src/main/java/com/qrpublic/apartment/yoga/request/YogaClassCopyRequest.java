package com.qrpublic.apartment.yoga.request;

import lombok.Data;

import java.util.List;

/**
 * Seller copy of one class onto several targets. Only the schedule info of the
 * source class (title, coach, capacity) travels; each target carries its own
 * date and time frame, and the weekday of the new weekly class is taken from
 * the target date. Buyers and registrations are never copied.
 */
@Data
public class YogaClassCopyRequest {
    private String token;
    /** the class whose info is duplicated */
    private String classCode;
    private List<Target> targets;

    /** One copy destination: date yyyy-MM-dd, times HH:mm 24h. */
    @Data
    public static class Target {
        private String date;
        private String startTime;
        private String endTime;
    }
}
