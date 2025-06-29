package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
	private long orderId;
	private String orderedTime;
	private String buyer;
	private String token;
	private boolean delivered;
	private boolean getMoney;
	private String sellerNote;
	private int amount;
	private String unit;
	private String note;

}
