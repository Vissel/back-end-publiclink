package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Buyer setup page payloads. token is the single-use setup code. */
@Data
public class BuyerSetupRequest {
    private String token;
    /** RSA-encrypted new password, only on complete */
    private String password;
}
