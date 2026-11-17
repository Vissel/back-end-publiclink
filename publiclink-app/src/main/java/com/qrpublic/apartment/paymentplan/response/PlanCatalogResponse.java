package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ACTIVE plan catalog, rendered by the UI as "3,000 | 1 day | 1 link".
 */
@Data
public class PlanCatalogResponse {

    private List<PlanItem> items;

    @Data
    public static class PlanItem {
        private String planCode;
        private BigDecimal fee;
        private int planDay;
        private int envCount;
        private String description;
    }
}
