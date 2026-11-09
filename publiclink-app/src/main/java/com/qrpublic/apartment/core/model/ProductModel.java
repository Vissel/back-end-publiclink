package com.qrpublic.apartment.core.model;

import lombok.Data;

import java.util.List;

@Data
public class ProductModel {
    private String productName;

    private int quantity;

    private String unit;

    private Double price;

    private int totalQuantity;

    private List<PictureModel> pictureModels;
}
