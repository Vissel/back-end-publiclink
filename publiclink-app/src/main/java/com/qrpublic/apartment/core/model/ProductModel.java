package com.qrpublic.apartment.core.model;

import lombok.Data;

@Data
public class ProductModel {
    private String productName;

    private int amount;

    private String unit;

    private Double price;

    private int totalAmount;
}
