package com.qrpublic.apartment.user.service.request;

import lombok.Data;

@Data
public class UserDeleteRequest {
    private String userId;
    private String userName;
}
