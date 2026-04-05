package com.qrpublic.apartment.adapter.authentication.response;

import com.qrpublic.apartment.adapter.template.BaseResponse;
import lombok.Data;

import java.util.Date;

@Data
public class NormalLoginResponse extends BaseResponse {
    private Boolean authenticated = Boolean.FALSE;
    private String message;
    private String username;
    private String token;
    private Date validTime;

}
