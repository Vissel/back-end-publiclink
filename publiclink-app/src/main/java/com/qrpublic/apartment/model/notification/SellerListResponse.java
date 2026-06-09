package com.qrpublic.apartment.model.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerListResponse {
    private List<SellerItem> sellers;
    private long total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerItem {
        private String username;
        private String name;
        private long environmentCount;
    }
}
