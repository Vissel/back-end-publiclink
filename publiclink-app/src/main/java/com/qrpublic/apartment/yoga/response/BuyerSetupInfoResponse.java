package com.qrpublic.apartment.yoga.response;

import lombok.Data;

/** Buyer setup page info screen. */
@Data
public class BuyerSetupInfoResponse {
    private String username;
    private String fullName;
    private String purpose;
    private boolean valid;
    private String expiresAt;
}
