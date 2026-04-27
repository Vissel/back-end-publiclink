package com.qrpublic.apartment.saleenv.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ListSellerRequestsRequest {
    @NotBlank
    private String sellerName;
}
