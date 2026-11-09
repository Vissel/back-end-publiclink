package com.qrpublic.apartment.order.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetDeliverRequest extends OrderRequest {
    @NotNull
    private Boolean delivered;
}
