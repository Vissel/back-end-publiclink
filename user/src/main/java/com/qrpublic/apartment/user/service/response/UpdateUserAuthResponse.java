package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class UpdateUserAuthResponse {
    private Boolean success;
    private int errorCode;
    private String errorMessage;
}
