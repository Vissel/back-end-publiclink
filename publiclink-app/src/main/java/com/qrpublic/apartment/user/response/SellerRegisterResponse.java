package com.qrpublic.apartment.user.response;

import com.qrpublic.apartment.requestmodel.BaseResponse;
import lombok.Data;

@Data
public class SellerRegisterResponse extends BaseResponse {
    private String username;
    private String maskedPassword;
    private String email;
    private String name;
    private String profileLink;
    private String message;
}
