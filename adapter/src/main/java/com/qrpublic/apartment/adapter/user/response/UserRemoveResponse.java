package com.qrpublic.apartment.adapter.user.response;

import lombok.Data;

@Data
public class UserRemoveResponse {
    private String userId;
    private Boolean deleted;
    private String message;
}
