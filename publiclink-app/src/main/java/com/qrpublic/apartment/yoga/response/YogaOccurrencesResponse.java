package com.qrpublic.apartment.yoga.response;

import com.qrpublic.apartment.core.model.YogaOccurrenceModel;
import lombok.Data;

import java.util.List;

/**
 * Shared list envelope for calendar occurrences and the below-calendar class
 * list (same shape, different range).
 */
@Data
public class YogaOccurrencesResponse {
    private List<YogaOccurrenceModel> items;
    /** true when the caller is the owning seller (edit affordances) */
    private boolean sellerView;
    /** plan summary for the logged-in buyer banner; null when none */
    private com.qrpublic.apartment.core.model.SellerPlanModel buyerPlan;
    private String productName;
    private java.math.BigDecimal dropInPrice;

    public static YogaOccurrencesResponse of(List<YogaOccurrenceModel> items, boolean sellerView) {
        YogaOccurrencesResponse response = new YogaOccurrencesResponse();
        response.setItems(items);
        response.setSellerView(sellerView);
        return response;
    }
}
