package com.qrpublic.apartment.chat.request;

import lombok.Data;

/**
 * Browser push subscription, exactly as PushSubscription.toJSON() reports it:
 * the endpoint the push service will POST to, plus the two client keys needed
 * to encrypt the payload for that browser alone.
 */
@Data
public class PushSubscriptionRequest {

    private String endpoint;

    private Keys keys;

    @Data
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}
