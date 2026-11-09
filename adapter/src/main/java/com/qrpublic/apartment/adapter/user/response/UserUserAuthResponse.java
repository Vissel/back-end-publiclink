package com.qrpublic.apartment.adapter.user.response;

import lombok.Data;

@Data
public class UserUserAuthResponse {
    private Boolean success;
    private int errorCode;
    private String errorMessage;
}
