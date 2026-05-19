package com.qrpublic.apartment.apiGateway.authentication.request;

import lombok.Data;

@Data
public class AuthenticatedTokenRequest {
    private String username;

    private long validTime;

    private String role;

    private String name;
}
