package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.OrderDTO;

public interface OrderService {

    OrderDTO addNewOrder(SaleEnvironment environment, OrderDTO requestOrderDTO);

    boolean setDelivery(long orderId, boolean isDelivered);

    boolean setGetMoney(long orderId, boolean isGetMoney);

    boolean setSellerNote(OrderDTO order);
}
