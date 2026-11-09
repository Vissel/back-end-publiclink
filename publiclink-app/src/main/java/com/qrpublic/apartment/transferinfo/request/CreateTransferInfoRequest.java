package com.qrpublic.apartment.transferinfo.request;

import lombok.Data;

/**
 * Owner (admin or seller) registers a bank / e-wallet account. Owner identity
 * is resolved from the caller, never passed in. DB ids are never used.
 */
@Data
public class CreateTransferInfoRequest {
    private String transferIssuer;
    private String issuerFullName;
    /** BANK | EWALLET | OTHER */
    private String type = "BANK";
    private String transferNumber;
    private String accountName;
    /** base64 data URI, same convention as picture data */
    private String qrImage;
    private String defaultMessage;
    private boolean setDefault;

    /**
     * Role the caller UI is acting in (ADMIN | SELLER). Only used as a
     * fallback label for owners the publiclink user table does not know about;
     * ownership itself always comes from the authenticated caller.
     */
    private String ownerHint;
}
