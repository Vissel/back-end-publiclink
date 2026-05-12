package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserAuthenTokenRequest {
    @NotBlank(message = "Username is required")
    private String username;

    private long validTime;

    private String role;
}
