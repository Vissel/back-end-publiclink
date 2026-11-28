package com.qrpublic.apartment.account.request;

import lombok.Data;

/**
 * Own-password change from the /self page. Both values are RSA-encrypted in
 * the browser and are only ever decrypted by the account service; the username
 * is never taken from this body but from the caller's JWT.
 */
@Data
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
}
