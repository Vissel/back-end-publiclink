package com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.response;

import lombok.Data;

import java.util.Date;

@Data
public class NormalLoginResponse {
    private Boolean authenticated = Boolean.FALSE;
    private String message;
    private String username;
    private String token;
    private Date validTime;

}
