package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
