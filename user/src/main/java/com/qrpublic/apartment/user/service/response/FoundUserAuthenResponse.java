package com.qrpublic.apartment.user.service.response;

import lombok.Data;

import java.util.Date;

@Data
public class FoundUserAuthenResponse {
    private String userName;
    private String authenticationToken;
    private Date createdAt;
    private Date expiredAt;
    private Boolean isActive;
    private int extendedNum;
}
