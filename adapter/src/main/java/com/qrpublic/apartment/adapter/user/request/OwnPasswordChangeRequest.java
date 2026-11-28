package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Self-service password change. The account proves the password it already has
 * and offers the one it wants next. Both values travel RSA-encrypted from the
 * browser and are only decrypted inside the account service, so no clear-text
 * password crosses a service boundary.
 */
@Data
public class OwnPasswordChangeRequest {
    @NotBlank
    private String userName;
    @NotBlank
    private String currentEncryptedPassword;
    @NotBlank
    private String newEncryptedPassword;
}
