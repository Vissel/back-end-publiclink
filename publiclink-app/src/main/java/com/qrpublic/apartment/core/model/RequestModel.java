package com.qrpublic.apartment.core.model;

import lombok.Data;

@Data
public class RequestModel {
    private String requestUuid;
    private String createdAt;
    private UserModel seller;
    private AuthenticationEnum authentication;
}
