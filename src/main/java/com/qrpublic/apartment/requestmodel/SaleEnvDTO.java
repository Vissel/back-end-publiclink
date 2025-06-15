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
public class SaleEnvDTO {
	private String createdAt;
	private String sellerName;
	private String sellerLink;
	private String sellerAuthLink;
	private String productName;
	private String publicLink;
	private String createdBy;
	private boolean envStatus;
	private List<OrderDTO> orders;

	// dont have sellerAuthLink
	public SaleEnvDTO(String createdAt, String sellerName, String sellerLink, String productName, String publicLink,
			String createdBy, boolean envStatus, List<OrderDTO> orders) {
		super();
		this.createdAt = createdAt;
		this.sellerName = sellerName;
		this.sellerLink = sellerLink;
		this.productName = productName;
		this.publicLink = publicLink;
		this.createdBy = createdBy;
		this.envStatus = envStatus;
		this.orders = orders;
	}

}
