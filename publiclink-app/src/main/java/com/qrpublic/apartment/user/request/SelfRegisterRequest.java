package com.qrpublic.apartment.user.request;

import lombok.Data;

/**
 * Open self-registration payload. Unlike the invite-only
 * {@link SellerRegisterRequest} there is no request uuid: the visitor picks
 * their own username and the account is created from scratch. The password
 * arrives RSA-encrypted from the browser and is never decrypted here.
 */
@Data
public class SelfRegisterRequest {
    private String username;
    /** RSA-encrypted new password */
    private String password;
    private String name;
    /** Contact email: used for plan and payment follow-up */
    private String email;
}
