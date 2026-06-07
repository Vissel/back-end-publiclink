package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String token;
    private Long productId;
    private String productName;
    private int amount;
    private String unit;
    private Double price;
    private int totalAmount;
    private List<PictureDTO> listPicProMap;
}
