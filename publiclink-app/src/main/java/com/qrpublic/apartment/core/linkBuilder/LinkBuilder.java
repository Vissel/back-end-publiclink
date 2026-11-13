package com.qrpublic.apartment.core.linkBuilder;

import org.springframework.web.util.UriComponentsBuilder;

public class LinkBuilder {

    public static String buildPublicLink(String validToken) {
        return UriComponentsBuilder.fromPath("/publink")
                .queryParam("token", validToken)
                .build()
                .toUriString();
    }

    public static String buildAuthenticationLink(String requestUuid, String validToken) {
        return UriComponentsBuilder.fromPath("/link")
                .queryParam("reqUuid", requestUuid)
                .queryParam("token", validToken)
                .build()
                .toUriString();
    }

    /**
     * Payment page URL handed to the seller. No authorization is required to
     * open it; the token carries the payment code claim.
     */
    public static String buildPaymentLink(String validToken) {
        return UriComponentsBuilder.fromPath("/payment")
                .queryParam("token", validToken)
                .build()
                .toUriString();
    }

    /**
     * Buyer password setup/reset page. The path is the frontend route; the
     * token is the single-use DB code, not a JWT.
     */
    public static String buildBuyerSetupPath(String tokenCode) {
        return UriComponentsBuilder.fromPath("/buyer-setup")
                .queryParam("token", tokenCode)
                .build()
                .toUriString();
    }

}
