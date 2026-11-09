package com.qrpublic.apartment.user.service.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank
    private String userName;  // Required to identify the user, but cannot be updated
    private String encryptedPassword;
    private String fullName;
    private String link;
    private String role;
}
