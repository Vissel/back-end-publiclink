package com.qrpublic.apartment.saleenv.request;

import lombok.Data;

@Data
public class ListEnvironmentRequest {
    private String createdAt;
    private String createdBy;
    private String sellerName;
    private String requestUuid;
}
