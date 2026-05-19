package com.qrpublic.apartment.saleenv.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaleUrlResponse {
    private String sellerName;
    private String publicLink;
    private String envState;
}
