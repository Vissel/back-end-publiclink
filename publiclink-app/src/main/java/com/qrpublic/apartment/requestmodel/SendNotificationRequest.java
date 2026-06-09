package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {
    private String title;
    private String message;
    private boolean selectAll;
    private List<NotificationTarget> targets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationTarget {
        private String sellerUsername;
        private List<String> requestUuids; // null or empty = all environments for that seller
    }
}
