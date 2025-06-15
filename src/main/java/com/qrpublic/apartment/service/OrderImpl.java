package com.qrpublic.apartment.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.OrderResponsitory;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.util.Utils;

@Service
public class OrderImpl implements OrderService {
	@Autowired
	OrderResponsitory orderRepo;

	@Override
	public OrderDTO addNewOrder(SaleEnvironment environment, String buyerName) {
		OrderDTO result = null;
		if (buyerName != null && !buyerName.isBlank()) {
			long orderedTime = System.currentTimeMillis();
			Order newOrder = new Order();
			newOrder.setBuyerName(buyerName);
			newOrder.setSaleEnvironment(environment);

			Order order = orderRepo.save(newOrder);
			if (order.getOrderId() != 0) {
				result = Utils.createOrderDTO(order, orderedTime, environment.getPublicLink());
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
