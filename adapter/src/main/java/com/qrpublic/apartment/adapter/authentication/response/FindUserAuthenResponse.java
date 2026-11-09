package com.qrpublic.apartment.adapter.authentication.response;

import lombok.Data;

import java.util.Date;

@Data
public class FindUserAuthenResponse {
    private String userName;
    private String authenticationToken;
    private Date createdAt;
    private Date expiredAt;
    private Boolean isActive;
    private int extendedNum;
}
