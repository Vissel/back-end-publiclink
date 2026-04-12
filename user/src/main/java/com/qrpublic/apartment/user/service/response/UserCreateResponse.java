package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class UserCreateResponse {
    private String userName;
    private String Password;
    private String fullName;
    private String link;
    private String role;
    private String message;
}
