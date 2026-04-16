package com.qrpublic.apartment.user.service.response;

import lombok.Data;

@Data
public class FoundUserResponse {
    private String userName;
    private String encodedPassword;
    private String name;
    private String email;
}
