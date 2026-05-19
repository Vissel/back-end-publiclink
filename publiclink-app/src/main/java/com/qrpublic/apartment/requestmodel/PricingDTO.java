package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingDTO {
    private int durationHours;
    private BigDecimal amount;
    private String currency;
    private String createdAt;
}
