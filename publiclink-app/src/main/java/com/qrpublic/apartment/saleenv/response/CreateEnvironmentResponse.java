package com.qrpublic.apartment.saleenv.response;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.product.response.CreateProductResponse;
import lombok.Data;

@Data
public class CreateEnvironmentResponse {
    private String authLink;
    private String requestUUID;
    private String urlString;
    private String createdAt;
    private String expired;
    private CreateProductResponse product;

    public String buildUrlString() {
        return "/link?" + LinkConstant.PARAM_REQUEST_UUID + "=" + this.requestUUID
                + "&" + LinkConstant.PARAM_TOKEN + "=" + this.authLink;
    }
}
