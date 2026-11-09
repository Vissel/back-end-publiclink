package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class ChangePasswordResponse {
    private String userName;
    private Boolean changed;
    private String message;
}
