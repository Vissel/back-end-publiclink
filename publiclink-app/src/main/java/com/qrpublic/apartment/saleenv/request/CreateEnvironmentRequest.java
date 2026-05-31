package com.qrpublic.apartment.saleenv.request;

import com.qrpublic.apartment.product.request.CreateProductRequest;
import com.qrpublic.apartment.requestmodel.PubUserRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    @NotNull
    private PubUserRequest sellerRequest;
    @NotBlank
    private String requestUuid;
    private CreateProductRequest productRequest;
}
