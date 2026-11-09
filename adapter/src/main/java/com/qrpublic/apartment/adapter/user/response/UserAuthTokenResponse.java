package com.qrpublic.apartment.adapter.user.response;

import lombok.Data;

import java.util.Date;

@Data
public class UserAuthTokenResponse {
    private String authenticationToken;
    private Date issueAt;
    private Date expire;
}
