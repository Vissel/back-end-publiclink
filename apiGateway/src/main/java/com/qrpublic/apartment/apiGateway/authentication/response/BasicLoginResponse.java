package com.qrpublic.apartment.apiGateway.authentication.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class BasicLoginResponse {
    private Boolean authenticated = Boolean.FALSE;
    private String message;
    private String username;
    private String token;
    private Date validUntil;
    private String refreshToken;
}
