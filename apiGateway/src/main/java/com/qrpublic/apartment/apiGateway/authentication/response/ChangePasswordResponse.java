package com.qrpublic.apartment.apiGateway.authentication.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangePasswordResponse {
    private Boolean changed;
    private String message;
}
