package com.qrpublic.apartment.user.request;

import com.qrpublic.apartment.requestmodel.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SellerRegisterRequest extends BaseRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String repeatPassword;
    private String email;
    private String name;
    private String profileLink;
}
