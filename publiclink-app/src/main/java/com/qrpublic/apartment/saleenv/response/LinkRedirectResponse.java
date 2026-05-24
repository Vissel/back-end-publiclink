package com.qrpublic.apartment.saleenv.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkRedirectResponse {
    /**
     * Frontend path to navigate to (e.g. /login?username=x&requestUuid=y)
     */
    private String destination;

    /**
     * Result status: REDIRECT, INVALID, or ERROR
     */
    private String status;
}
