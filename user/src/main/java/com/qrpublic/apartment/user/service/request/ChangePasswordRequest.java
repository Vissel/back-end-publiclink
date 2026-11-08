package com.qrpublic.apartment.user.service.request;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String userName;
    private String encryptedPassword;
}
