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
    /** the class whose schedule info is duplicated */
    private String classCode;
    /**
     * Batch-level overrides the seller may edit in the copy dialog. Null keeps
     * the source value; an empty coach clears it, an empty title keeps it
     * because the column is required.
     */
    private String title;
    private String coach;
    private List<Target> targets;

    /** One copy destination: date yyyy-MM-dd, times HH:mm 24h. */
    @Data
    public static class Target {
        private String date;
        private String startTime;
        private String endTime;
    }
}
