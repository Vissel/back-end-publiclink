package com.qrpublic.apartment.adapter.user.response;

import lombok.Data;

@Data
public class UserChangePasswordResponse {
    private String userName;
    private Boolean changed;
    private String message;
}
