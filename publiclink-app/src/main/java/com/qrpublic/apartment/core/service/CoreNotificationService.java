package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.Notification;
import com.qrpublic.apartment.entity.NotificationRecipient;
import com.qrpublic.apartment.repository.NotificationRecipientRepository;
import com.qrpublic.apartment.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Core layer wrapper of the notification tables. Reused by the payment flow to
 * inform admins (recipients are stored per username, exactly like sellers).
 */
@Slf4j
@Service
public class CoreNotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository recipientRepository;

    @Transactional
    public void notifyUsernames(List<String> usernames, String title, String message,
            String targetType, String createdBy) {
        if (usernames == null || usernames.isEmpty()) {
            log.warn("No notification recipient for title [{}], skipping", title);
            return;
        }

        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetType(targetType);
        notification.setCreatedBy(createdBy);
        Notification saved = notificationRepository.save(notification);

        List<NotificationRecipient> recipients = new ArrayList<>();
        for (String username : usernames) {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setNotification(saved);
            recipient.setSellerUsername(username);
            recipient.setRead(false);
            recipients.add(recipient);
        }
        recipientRepository.saveAll(recipients);
        log.info("Notification [{}] ({}) delivered to {} recipient(s)", saved.getId(), targetType, recipients.size());
    }
}
