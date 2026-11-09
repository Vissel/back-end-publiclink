package com.qrpublic.apartment.apiGateway.authentication.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Password change ticket is required")
    private String ticket;

    @NotBlank(message = "Encrypted password is required")
    private String encryptedPassword;
}
