package com.qrpublic.apartment.plan.request;

import com.qrpublic.apartment.product.request.CreateProductRequest;
import com.qrpublic.apartment.requestmodel.PubUserRequest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePlanRequest {
    @NotNull
    private PubUserRequest sellerRequest;

    private CreateProductRequest productRequest;

    private String startTime;
}
