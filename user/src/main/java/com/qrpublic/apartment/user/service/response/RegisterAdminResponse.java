package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class RegisterAdminResponse {
    private String userName;
    private String password;
    private String role;
}
