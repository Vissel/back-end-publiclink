package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRegisterRequest {
    @NotBlank
    @NotNull
    private String userName;
    private String encryptedPassword;
    private String fullName;
    private String link;
    private String role;
}
