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

}
