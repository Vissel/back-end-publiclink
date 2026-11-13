package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The VAPID public key the browser needs to subscribe, plus whether push is
 * configured at all - the console hides its notification switch when it is not,
 * instead of failing the subscribe call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VapidPublicKeyResponse {

    private String publicKey;

    private boolean configured;
}
