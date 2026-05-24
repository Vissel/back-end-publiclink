package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class UserUpdateResponse {
    private String userName;
    private String password;
    private String fullName;
    private String link;
    private String role;
    private String message;
    private boolean success;
}
