package com.qrpublic.apartment.model.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerEnvironmentListResponse {
    private List<SellerEnvironments> sellers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerEnvironments {
        private String username;
        private String sellerName;
        private List<EnvironmentItem> environments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentItem {
        private String requestUuid;
        private String productName;
        private String createdAt;
        private boolean active;
        private String endedAt;
    }
}
