package com.qrpublic.apartment.saleenv.request;

import lombok.Data;

@Data
public class AddOrderRequest {
    private String token;
    private String buyerName;
    private int amount;
    private String unit;
    private String note;
}
