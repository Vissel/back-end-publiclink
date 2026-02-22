package com.qrpublic.apartment.integration.authentication.request;

import lombok.Data;

@Data
public class NormalLoginRequest {
    private String username;
    private String encryptedPassword;
    private long validDuring;
}
