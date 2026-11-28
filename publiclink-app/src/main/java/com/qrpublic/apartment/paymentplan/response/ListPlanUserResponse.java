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
        /** CREATE | ACTIVE | DEACTIVE | EXTENDED | CONVERTED */
        private String planState;
        /** PRODUCT or BOOKING, so the admin can see what a row actually buys */
        private String planGroup;
        private String activeDate;
        private String endedDate;
        /**
         * Start the seller asked for when buying the plan themselves, so the
         * admin sees what the seller expects before choosing an activation date.
         */
        private String requestedStartAt;
        /** days bought on another plan; set on an extension order row */
        private Integer extendDays;
        /** the plan this extension order adds its days to */
        private String extendsPlanUserCode;
        /** the plan this conversion order replaces */
        private String convertsPlanUserCode;
        /** amount due on a conversion order: the difference between the plans */
        private BigDecimal convertFeeDelta;
        private String createdAt;
        private String paymentCode;
        /** CREATE | WAITING_CONFIRM | CONFIRMED | PAID | REJECTED */
        private String paymentStatus;
        /** relative payment URL, e.g. /payment?token=... */
        private String paymentUrl;
    }
}
