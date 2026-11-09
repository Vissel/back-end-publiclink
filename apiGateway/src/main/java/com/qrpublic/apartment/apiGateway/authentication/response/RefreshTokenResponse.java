package com.qrpublic.apartment.apiGateway.authentication.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
public class RefreshTokenResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private List<String> roles;
    private Date expiresAt;
    private String message;
}
