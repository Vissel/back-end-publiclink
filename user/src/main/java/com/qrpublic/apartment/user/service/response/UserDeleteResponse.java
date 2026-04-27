package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class UserDeleteResponse {
    private String userId;
    private Boolean deleted;
    private String message;
}
