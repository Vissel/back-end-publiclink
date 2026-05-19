package com.qrpublic.apartment.requestmodel;

import lombok.Data;

@Data
public class PubUserRequest {
    private String username;
    private String link;
    private String name;
    private PriceRequest price;
}
