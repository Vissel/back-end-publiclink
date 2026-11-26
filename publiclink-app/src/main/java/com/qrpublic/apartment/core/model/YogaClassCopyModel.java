package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outcome of a class copy: the classes that were created plus one entry per
 * target that was refused. Targets are independent, so a batch reports partial
 * success instead of rolling the whole copy back.
 */
@Data
@Builder
public class YogaClassCopyModel {
    private List<YogaClassModel> created;
    private List<FailedTarget> failed;

    /** A rejected target, echoed back with the reason the seller can read. */
    @Data
    @Builder
    public static class FailedTarget {
        private String date;
        private String startTime;
        private String endTime;
        private String reason;
    }
}
