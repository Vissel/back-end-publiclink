package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuperAdminRegisterRequest {
    @NotBlank
    @NotNull
    private String userName;
    private String role;
}
