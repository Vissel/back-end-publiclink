package com.qrpublic.apartment.user.service.request;

import lombok.Data;

/**
 * Own-password change: the current password is verified against the stored
 * hash before the new one is written.
 */
@Data
public class ChangeOwnPasswordRequest {
    private String userName;
    private String currentEncryptedPassword;
    private String newEncryptedPassword;
}
