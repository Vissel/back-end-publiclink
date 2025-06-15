package com.qrpublic.apartment.requestmodel;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

	private String productName;

	private int amount;

	private String unit;

	private Double price;

	private int total_amount;

	List<PictureDTO> listPicProMap;
}
