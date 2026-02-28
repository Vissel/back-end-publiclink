package com.qrpublic.apartment.user.interfaces.request;

import com.qrpublic.apartment.requestmodel.RoleEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserCreateRequest {
    @NotBlank
    @NotNull
    private String userName;
    private String encryptedPassword;
    private String fullName;
    private String link;
    private String role;
}
