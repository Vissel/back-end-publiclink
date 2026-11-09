package com.qrpublic.apartment.requestmodel;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PubUserRequest {
    private String username;
    private String link;
    private String name;
    @NotNull
    private PriceRequest price;
}
