package com.qrpublic.apartment.model.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private String targetType;
    private String requestUuid;
    private boolean isRead;
    private String createdAt;
    private String senderName;
    private String senderEmail;
}
