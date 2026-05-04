package com.qrpublic.apartment.model.convertor;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.requestmodel.OrderDTO;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class OrderConvertor {
    public static OrderDTO createOrderDTO(com.qrpublic.apartment.entity.Order order, long time, String publicLink) {
        SimpleDateFormat formater = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
        formater.setTimeZone(TimeZone.getTimeZone("UTC"));
        String orderTime = formater.format(new Date(time));
        return new OrderDTO(order.getOrderId(), orderTime, order.getBuyerName(), publicLink, order.isDelivered(),
                order.isGetMoney(), order.getSellerNote(), order.getAmount(), order.getUnit(), order.getNote());

    }
}
