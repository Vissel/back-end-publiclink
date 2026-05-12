package com.qrpublic.apartment.apiGateway.authentication.response;

import lombok.Data;

import java.util.Date;

@Data
public class AuthenticatedTokenResponse {
    private String authenticatedToken;
    private Date issueAt;
    private Date expire;
}
