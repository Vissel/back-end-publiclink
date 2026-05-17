package com.qrpublic.apartment.user.request;

import lombok.Data;

@Data
public class ListUserRequest {
    private String username;
    private String name;
    private String email;
    private String role;
}
