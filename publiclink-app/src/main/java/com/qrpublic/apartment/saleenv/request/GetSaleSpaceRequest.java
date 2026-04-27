package com.qrpublic.apartment.saleenv.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class GetSaleSpaceRequest {
    @NotBlank
    private String token;
    private Map<String, String> headers;
}
