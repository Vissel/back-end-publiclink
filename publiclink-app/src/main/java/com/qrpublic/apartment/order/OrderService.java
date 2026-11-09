package com.qrpublic.apartment.order;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.order.request.GetMoneyRequest;
import com.qrpublic.apartment.order.request.SellerNoteRequest;
import com.qrpublic.apartment.order.request.SetDeliverRequest;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.saleenv.request.AddOrderRequest;
import com.qrpublic.apartment.saleenv.response.AddOrderResponse;
import com.qrpublic.apartment.template.model.Result;

public interface OrderService {

    OrderDTO addNewOrder(SaleEnvironment environment, OrderDTO requestOrderDTO);

    boolean setDelivery(SetDeliverRequest setDeliverRequest);

    boolean setGetMoney(GetMoneyRequest getMoneyRequest);

    boolean setSellerNote(SellerNoteRequest request);

    Result<AddOrderResponse> addOrder(AddOrderRequest request);
}
