package com.qrpublic.apartment.adapter.authentication.response;

import lombok.Data;

@Data
public class FindUserResponse {
    private String userName;
    private String encodedPassword;
    private String name;
    private String email;
}
