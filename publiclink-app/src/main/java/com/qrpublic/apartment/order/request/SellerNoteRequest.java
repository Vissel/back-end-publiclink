package com.qrpublic.apartment.order.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SellerNoteRequest extends OrderRequest {
    @NotBlank
    private String sellerNote;
}
