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

    /**
     * Optional epoch millis: when the sale window opens. Blank or in the past
     * means "starts now"; a future value schedules the sale.
     */
    private Long startAt;

    /**
     * Optional epoch millis: when the sale window closes. Blank falls back to
     * now + price.durationHours. Never accepted past the paid plan end.
     */
    private Long endAt;
}
