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

	private Long productId;
	private String productName;

	private int amount;

	private String unit;

	private Double price;

	private int total_amount;

	List<PictureDTO> listPicProMap;

	public ProductDTO(String productName, int amount, String unit, Double price, int total_amount,
			List<PictureDTO> listPicProMap) {
		this.productName = productName;
		this.amount = amount;
		this.unit = unit;
		this.price = price;
		this.total_amount = total_amount;
		this.listPicProMap = listPicProMap;
	}
}
