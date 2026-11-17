package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ChatPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatPushSubscriptionRepository extends JpaRepository<ChatPushSubscription, Long> {

    Optional<ChatPushSubscription> findByEndpoint(String endpoint);

    /**
     * Every subscription of every administrator. The bridge has one support
     * audience today, so a push fans out to all of them and the service worker
     * decides which window actually shows the notification.
     */
    List<ChatPushSubscription> findAllByOrderByIdAsc();
}
