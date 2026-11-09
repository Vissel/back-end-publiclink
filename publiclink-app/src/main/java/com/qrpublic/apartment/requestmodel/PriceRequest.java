package com.qrpublic.apartment.requestmodel;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceRequest {
    private int durationHours;
    private BigDecimal priceAmount;
    private String currency;
}
