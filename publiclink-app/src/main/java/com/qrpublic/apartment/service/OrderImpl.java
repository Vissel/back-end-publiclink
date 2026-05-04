package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.model.convertor.OrderConvertor;
import com.qrpublic.apartment.repository.OrderRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderImpl implements OrderService {
    @Autowired
    OrderRepository orderRepo;

    @Override
    public OrderDTO addNewOrder(SaleEnvironment environment, OrderDTO requestOrderDTO) {
        OrderDTO result = null;
        if (Utils.isValidStr(requestOrderDTO.getBuyer())) {
            long orderedTime = System.currentTimeMillis();
            Order newOrder = new Order();
            newOrder.setBuyerName(requestOrderDTO.getBuyer());
            newOrder.setAmount(requestOrderDTO.getAmount());
            newOrder.setNote(requestOrderDTO.getNote());
            newOrder.setSaleEnvironment(environment);

            Order order = orderRepo.save(newOrder);
            if (order.getOrderId() != 0) {
                result = OrderConvertor.createOrderDTO(order, orderedTime, environment.getPublicLink());
            }
        }
        return result;
    }

    @Override
    public boolean setDelivery(long orderId, boolean isDelivered) {
        Optional<Order> order = orderRepo.findById(orderId);
        if (order.isPresent()) {
            order.get().setDelivered(isDelivered);
            return orderRepo.save(order.get()).isDelivered();
        }
        return false;
    }

    @Override
    public boolean setGetMoney(long orderId, boolean isGetMoney) {
        Optional<Order> order = orderRepo.findById(orderId);
        if (order.isPresent()) {
            order.get().setGetMoney(isGetMoney);
            return orderRepo.save(order.get()).isGetMoney();
        }
        return false;
    }

    @Override
    public boolean setSellerNote(OrderDTO orderDTO) {
        Optional<Order> findOrder = orderRepo.findById(orderDTO.getOrderId());
        if (findOrder.isPresent()) {
            findOrder.get().setSellerNote(orderDTO.getSellerNote());
            return orderRepo.save(findOrder.get()).getSellerNote().equals(orderDTO.getSellerNote());
        }
        return false;
    }
}
