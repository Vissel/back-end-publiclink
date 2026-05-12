package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.OrderModel;
import com.qrpublic.apartment.core.model.ProductModel;
import com.qrpublic.apartment.core.model.SaleEnvironmentModel;
import com.qrpublic.apartment.core.model.SellerModel;
import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoreEnvironmentService {

    @Autowired
    private SaleEnvironmentRepository repo;

    @Transactional
    public List<SaleEnvironmentModel> getEnvironments(Pageable pageable) {
        List<SaleEnvironment> all = repo.findAllWithProducts();
        List<String> ids = all.stream().map(SaleEnvironment::getEnvId).toList();
        Map<String, List<Order>> ordersMap = repo.findAllWithOrdersByIds(ids).stream()
                .collect(Collectors.toMap(SaleEnvironment::getEnvId,
                        se -> se.getListOrder() == null ? List.of() : se.getListOrder()));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return all.subList(start, end).stream()
                .map(se -> toModel(se, ordersMap.getOrDefault(se.getEnvId(), List.of())))
                .toList();
    }

    private SaleEnvironmentModel toModel(SaleEnvironment env, List<Order> orders) {
        SaleEnvironmentModel model = new SaleEnvironmentModel();
        model.setRequestUUID(env.getRequest().getReqUUID());
        model.setCreatedAt(Utils.formatTimeStamp(env.getCreatedAt()));
        model.setPublicLink(env.getPublicLink());
        model.setEnvState(env.isState()
                ? com.qrpublic.apartment.core.model.EnvStateEnum.ACTIVE
                : com.qrpublic.apartment.core.model.EnvStateEnum.INACTIVE);

        SellerModel seller = new SellerModel();
        seller.setName(env.getRequest().getSellerName());
        if (env.getRequest().getCreatedBy() != null) {
            seller.setUsername(env.getRequest().getCreatedBy().getUserName());
            seller.setName(env.getRequest().getCreatedBy().getName());
        }
        model.setSeller(seller);

        model.setProducts(env.getRequest().getProducts() == null ? List.of()
                : env.getRequest().getProducts().stream().map(this::toProductModel).toList());

        model.setOrders(orders.stream().map(this::toOrderModel).toList());

        return model;
    }

    private ProductModel toProductModel(Product p) {
        ProductModel m = new ProductModel();
        m.setProductName(p.getProductName());
        m.setAmount(p.getAmount());
        m.setUnit(p.getUnit());
        m.setPrice(p.getPrice());
        m.setTotalAmount(p.getTotal_amount());
        return m;
    }

    private OrderModel toOrderModel(Order o) {
        OrderModel m = new OrderModel();
        m.setBuyerName(o.getBuyerName());
        return m;
    }
}
