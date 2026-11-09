package com.qrpublic.apartment.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

import com.qrpublic.apartment.core.model.RequestModel;

import lombok.Data;

@Data
public class PriceModel {
    private RequestModel requestModel;
    
    private int durationHours;

    private BigDecimal amount;

    private String currency;

    private Timestamp createdAt;
}
