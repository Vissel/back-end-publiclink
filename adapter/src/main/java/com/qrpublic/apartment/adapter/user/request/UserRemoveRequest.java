package com.qrpublic.apartment.adapter.user.request;

import lombok.Data;

@Data
public class UserRemoveRequest {
    @Deprecated
    private String userId;
    private String userName;
}
