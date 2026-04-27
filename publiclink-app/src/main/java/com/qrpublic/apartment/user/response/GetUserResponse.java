package com.qrpublic.apartment.user.response;

import lombok.Data;

@Data
public class GetUserResponse {
    private String username;
    private String name;
    private String email;
    private String role;
}
