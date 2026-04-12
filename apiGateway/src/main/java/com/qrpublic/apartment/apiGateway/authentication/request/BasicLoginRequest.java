package com.qrpublic.apartment.apiGateway.authentication.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BasicLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String encryptedPassword;
    private long validDuring;
}
