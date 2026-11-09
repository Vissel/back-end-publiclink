package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoreOrderService {
    @Autowired
    OrderRepository orderRepository;

    public List<Order> getOrdersByEnvironment(SaleEnvironment environment) {
        return orderRepository.findOrdersBySaleEnvironment(environment);
    }

    public Map<String, List<Order>> getOrdersMapByEnvironmentIds(List<String> envIds) {
        return orderRepository.findOrdersByEnvironmentIds(envIds).stream()
                .collect(Collectors.groupingBy(o -> o.getSaleEnvironment().getEnvId()));
    }
}
