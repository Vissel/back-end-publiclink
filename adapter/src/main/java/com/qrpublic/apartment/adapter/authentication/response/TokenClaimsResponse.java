package com.qrpublic.apartment.adapter.authentication.response;

import lombok.Data;

@Data
public class TokenClaimsResponse {
    private String subject;
    private String username;
    private String name;
    private String role;
}
