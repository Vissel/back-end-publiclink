package com.qrpublic.apartment.saleenv.response;

import lombok.Data;

@Data
public class CreateRequestIdResponse {
    private String requestUuid;
    private String createdAt;
    private String sellerName;
}
