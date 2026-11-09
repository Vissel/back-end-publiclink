package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The one-time Zalo consent handoff: open {@code authorizeUrl} as the OA
 * administrator, then Zalo redirects back to {@code redirectUri} with a code and
 * this exact {@code state}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZaloConsentResponse {

    private String authorizeUrl;

    private String state;

    /** Callback URL that must be registered in the Zalo app settings, character for character. */
    private String redirectUri;
}
