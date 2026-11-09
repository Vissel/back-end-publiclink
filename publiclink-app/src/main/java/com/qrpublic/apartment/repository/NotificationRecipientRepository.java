package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    @Query("SELECT nr FROM NotificationRecipient nr JOIN FETCH nr.notification n " +
            "WHERE nr.sellerUsername = :username " +
            "ORDER BY n.createdAt DESC")
    Page<NotificationRecipient> findBySellerUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT nr FROM NotificationRecipient nr JOIN FETCH nr.notification n " +
            "WHERE nr.sellerUsername = :username AND nr.isRead = :isRead " +
            "ORDER BY n.createdAt DESC")
    Page<NotificationRecipient> findBySellerUsernameAndIsRead(@Param("username") String username,
            @Param("isRead") boolean isRead,
            Pageable pageable);

    long countBySellerUsernameAndIsRead(@Param("username") String username, @Param("isRead") boolean isRead);

    @Query("SELECT nr FROM NotificationRecipient nr JOIN FETCH nr.notification n " +
            "WHERE nr.sellerUsername = :username AND nr.notification.id = :notificationId")
    Optional<NotificationRecipient> findBySellerUsernameAndNotificationId(@Param("username") String username,
            @Param("notificationId") Long notificationId);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationRecipient nr SET nr.isRead = true, nr.readAt = :now " +
            "WHERE nr.sellerUsername = :username AND nr.isRead = false")
    int markAllAsRead(@Param("username") String username, @Param("now") Timestamp now);
}
