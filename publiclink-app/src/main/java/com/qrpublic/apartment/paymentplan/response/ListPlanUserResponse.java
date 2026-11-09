package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Paginated plan-assignment list for the setup page tables.
 */
@Data
public class ListPlanUserResponse {

    private long total;
    private List<PlanUserItem> items;

    @Data
    public static class PlanUserItem {
        private String planUserCode;
        private String username;
        private String displayName;
        private BigDecimal fee;
        private int planDay;
        private int envQuota;
        private int usedCount;
        private int remaining;
        /** CREATE | ACTIVE | DEACTIVE */
        private String planState;
        private String activeDate;
        private String endedDate;
        private String createdAt;
        private String paymentCode;
        /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
        private String paymentStatus;
    }
}
