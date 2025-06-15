package com.qrpublic.apartment.requestmodel;

import lombok.Getter;

@Getter
public enum RoleEnum {
	ADMIN("Admin"), USER("User"), SELLER("Seller"), BUYER("Buyer");

	private String role;

	private RoleEnum(String role) {
		this.role = role;
	}
}
