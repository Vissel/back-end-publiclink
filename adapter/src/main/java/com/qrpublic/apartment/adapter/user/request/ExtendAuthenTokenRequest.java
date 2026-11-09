package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExtendAuthenTokenRequest {
    @NotBlank(message = "Existing token is required")
    private String existingToken;

    private long extensionMillis;
}
