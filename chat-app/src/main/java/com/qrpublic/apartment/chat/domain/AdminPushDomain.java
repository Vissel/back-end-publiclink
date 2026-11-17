package com.qrpublic.apartment.chat.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.core.model.ChatPushSubscriptionModel;
import com.qrpublic.apartment.chat.core.service.CorePushService;
import com.qrpublic.apartment.chat.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Browser push for the administrator console: the only channel that still
 * reaches the support team when the browser is minimised, on another tab, or
 * not open at all.
 *
 * Deliberately always sends. The service worker decides whether the event
 * becomes an OS notification or a message to the focused window, so this side
 * needs no idea of what the administrator is looking at, and a second machine
 * with the same account still hears about it.
 */
@Slf4j
@Component
public class AdminPushDomain {

    /** Push services reject payloads over 4 KB; a preview never needs that. */
    private static final int BODY_LIMIT = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    CorePushService corePushService;

    @Value("${chat.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${chat.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${chat.vapid.subject:mailto:admin@share-link.top}")
    private String vapidSubject;

    @Value("${chat.push.ttl-seconds:3600}")
    private int pushTtlSeconds;

    /** Built on first use: an unconfigured server must still boot and chat. */
    private volatile PushService pushService;

    public boolean isConfigured() {
        return notBlank(vapidPublicKey) && notBlank(vapidPrivateKey);
    }

    public String getPublicKey() {
        return vapidPublicKey;
    }

    /**
     * Stores a subscription. The endpoint is validated because it is a URL this
     * server will POST to with a signed request: restricting it to https (plus
     * plain http for a local browser test) keeps the service from being turned
     * into an arbitrary request relay.
     */
    public ChatPushSubscriptionModel subscribe(String adminUsername, String endpoint, String p256dh, String auth) {
        if (!isUsableEndpoint(endpoint)) {
            throw new ApplicationException("Push endpoint must be an https URL.", 400);
        }
        if (!notBlank(p256dh) || !notBlank(auth)) {
            throw new ApplicationException("Push subscription keys are required.", 400);
        }
        ChatPushSubscriptionModel saved = corePushService.upsert(adminUsername, endpoint.trim(), p256dh, auth);
        log.info("Chat push subscription stored for administrator {} ({} known)",
                adminUsername, corePushService.listAll().size());
        return saved;
    }

    public void unsubscribe(String endpoint) {
        if (!notBlank(endpoint)) {
            return;
        }
        corePushService.removeByEndpoint(endpoint.trim());
    }

    /**
     * Fire-and-forget fan-out. Never on the caller's thread: a push service can
     * take seconds, and a visitor's message POST must not wait for it.
     */
    public void notifyAdmins(ChatConversationModel conversation, ChatMessageModel message) {
        if (!isConfigured()) {
            return;
        }
        Mono.fromRunnable(() -> doNotify(conversation, message))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.warn("Chat push fan-out failed: {}", error.getMessage()));
    }

    private void doNotify(ChatConversationModel conversation, ChatMessageModel message) {
        List<ChatPushSubscriptionModel> subscriptions = corePushService.listAll();
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = buildPayload(conversation, message);
        PushService service = pushService();
        if (service == null) {
            return;
        }
        for (ChatPushSubscriptionModel subscription : subscriptions) {
            send(service, subscription, payload);
        }
    }

    private void send(PushService service, ChatPushSubscriptionModel subscription, String payload) {
        try {
            Notification notification = new Notification(subscription.getEndpoint(),
                    subscription.getP256dh(), subscription.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8), pushTtlSeconds);
            HttpResponse response = service.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // Gone or unsubscribed: keeping the row only slows every later send.
                corePushService.removeByEndpoint(subscription.getEndpoint());
                log.info("Dropped expired chat push subscription for {}", subscription.getAdminUsername());
            } else if (status < 400) {
                corePushService.touch(subscription.getEndpoint());
            } else {
                log.warn("Chat push answered {} for {}", status, subscription.getAdminUsername());
            }
        } catch (Exception e) {
            // One dead subscription must not stop the others.
            log.warn("Chat push failed for {}: {}", subscription.getAdminUsername(), e.getMessage());
        }
    }

    private String buildPayload(ChatConversationModel conversation, ChatMessageModel message) {
        PushPayload body = new PushPayload(
                "Khách #" + (conversation.getClientTag() == null ? "????" : conversation.getClientTag()),
                preview(message.getContent()),
                conversation.getUuid(),
                message.getId());
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Unable to serialise chat push payload: {}", e.getMessage());
            return "{\"title\":\"Chat\",\"body\":\"New message\"}";
        }
    }

    private PushService pushService() {
        PushService local = pushService;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (pushService == null) {
                try {
                    pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
                } catch (Exception e) {
                    log.error("Chat push keys are unusable, browser push disabled: {}", e.getMessage());
                    return null;
                }
            }
            return pushService;
        }
    }

    private static boolean isUsableEndpoint(String endpoint) {
        if (!notBlank(endpoint)) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme();
            return "https".equals(scheme)
                    || ("http".equals(scheme)
                            && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())));
        } catch (Exception e) {
            return false;
        }
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() > BODY_LIMIT ? flat.substring(0, BODY_LIMIT) + "…" : flat;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Shape the service worker reads; keep the field names in sync with chat-sw.js.
     */
    private record PushPayload(String title, String body, String conversationUuid, Long messageId) {
    }
}
