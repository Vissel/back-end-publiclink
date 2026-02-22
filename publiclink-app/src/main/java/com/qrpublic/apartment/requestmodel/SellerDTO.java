package com.qrpublic.apartment.requestmodel;

import com.qrpublic.apartment.entity.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SellerDTO extends AuthToken {
	private String username;
	private String link;
	private String productName;

	/**
	 * considering unuse
	 * 
	 * @param seller
	 */
	public SellerDTO(User seller) {
		this.username = seller.getName();
		this.link = seller.getLink();
	}
}
