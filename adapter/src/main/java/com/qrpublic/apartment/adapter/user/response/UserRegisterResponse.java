package com.qrpublic.apartment.adapter.user.response;

import lombok.Data;

/**
 *
 */
@Data
public class UserRegisterResponse {
    private String userName;
    private String password;
    private String fullName;
    private String link;
    private String role;
    private String message;
}
