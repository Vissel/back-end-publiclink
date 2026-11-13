package com.qrpublic.apartment.chat.core.service;

import com.qrpublic.apartment.chat.core.model.ChatPushSubscriptionModel;
import com.qrpublic.apartment.chat.entity.ChatPushSubscription;
import com.qrpublic.apartment.chat.repository.ChatPushSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Core layer for push subscriptions: the only place that touches the entity.
 */
@Slf4j
@Service
public class CorePushService {

    @Autowired
    ChatPushSubscriptionRepository chatPushSubscriptionRepository;

    /**
     * Register or refresh a subscription. Keyed on the endpoint, which the
     * browser reuses while it rotates the keys, so repeated subscribes from the
     * same window converge on one row.
     */
    @Transactional
    public ChatPushSubscriptionModel upsert(String adminUsername, String endpoint, String p256dh, String auth) {
        ChatPushSubscription subscription = chatPushSubscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(ChatPushSubscription::new);
        boolean fresh = subscription.getId() == null;
        subscription.setAdminUsername(adminUsername);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(p256dh);
        subscription.setAuth(auth);
        if (fresh) {
            // created_at is NOT NULL in the schema and Hibernate sends an explicit
            // null for an unset field, so the column default never applies.
            subscription.setCreatedAt(Timestamp.from(Instant.now()));
        }
        return toModel(chatPushSubscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<ChatPushSubscriptionModel> listAll() {
        return chatPushSubscriptionRepository.findAllByOrderByIdAsc().stream()
                .map(this::toModel)
                .toList();
    }

    /** A push service answered 404/410: the subscription is gone for good. */
    @Transactional
    public void removeByEndpoint(String endpoint) {
        chatPushSubscriptionRepository.findByEndpoint(endpoint).ifPresent(chatPushSubscriptionRepository::delete);
    }

    /** Records the last delivery that the browser actually accepted. */
    @Transactional
    public void touch(String endpoint) {
        Optional<ChatPushSubscription> found = chatPushSubscriptionRepository.findByEndpoint(endpoint);
        if (found.isEmpty()) {
            return;
        }
        ChatPushSubscription subscription = found.get();
        subscription.setLastSeenAt(Timestamp.from(Instant.now()));
        chatPushSubscriptionRepository.save(subscription);
    }

    private ChatPushSubscriptionModel toModel(ChatPushSubscription subscription) {
        ChatPushSubscriptionModel model = new ChatPushSubscriptionModel();
        model.setId(subscription.getId());
        model.setAdminUsername(subscription.getAdminUsername());
        model.setEndpoint(subscription.getEndpoint());
        model.setP256dh(subscription.getP256dh());
        model.setAuth(subscription.getAuth());
        model.setLastSeenAt(subscription.getLastSeenAt());
        return model;
    }
}
