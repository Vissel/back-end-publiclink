package com.qrpublic.apartment.adapter.user.request;

import lombok.Data;

import java.util.Date;

@Data
public class UserUserAuthRequest {
    private String userName;
    private String authToken;
    private Date expire;
    private int extendedNum;

}
