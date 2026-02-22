package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.OrderDTO;

public interface OrderService {

	public OrderDTO addNewOrder(SaleEnvironment environment, OrderDTO requestOrderDTO);

	public boolean setDelivery(long orderId, boolean isDelivered);

	public boolean setGetMoney(long orderId, boolean isGetMoney);

	public boolean setSellerNote(OrderDTO order);
}
