package com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NormalLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String encryptedPassword;
    private long validDuring;
}
