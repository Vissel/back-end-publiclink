package com.qrpublic.apartment.requestmodel;

import lombok.Data;

@Data
public class VietQrRequest {
	private String bankCode;
	private String accountNumber;
	private String accountName;
	private Double amount;
	private String description;
}