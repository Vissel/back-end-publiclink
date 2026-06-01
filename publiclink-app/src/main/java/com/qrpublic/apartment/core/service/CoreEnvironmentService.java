package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.OrderModel;
import com.qrpublic.apartment.core.model.ProductModel;
import com.qrpublic.apartment.core.model.SaleEnvironmentModel;
import com.qrpublic.apartment.core.model.SellerModel;
import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.Pricing;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.PricingRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoreEnvironmentService {

    @Autowired
    private SaleEnvironmentRepository repo;

    @Autowired
    private CoreOrderService coreOrderService;
    @Autowired
    private CoreProductService coreProductService;
    @Autowired
    private PricingRepository pricingRepository;

    @Transactional
    public Page<SaleEnvironmentModel> getEnvironments(Pageable pageable) {
        Page<SaleEnvironment> resultPage = repo.findAll(pageable);
        List<SaleEnvironmentModel> environmentModels = toEnvironmentModels(resultPage.getContent());
        return new PageImpl<>(environmentModels, pageable, resultPage.getTotalElements());
    }

    @Transactional
    public Page<SaleEnvironmentModel> getEnvironments(Pageable pageable, String createdAt, String createdBy,
                                                      String sellerName, String requestUuid) {
        Page<SaleEnvironment> resultPage = repo.findByFilters(createdAt, createdBy, sellerName, requestUuid, pageable);
        List<SaleEnvironmentModel> environmentModels = toEnvironmentModels(resultPage.getContent());
        return new PageImpl<>(environmentModels, pageable, resultPage.getTotalElements());
    }

    private List<SaleEnvironmentModel> toEnvironmentModels(List<SaleEnvironment> saleEnvironmentList) {
        if (saleEnvironmentList.isEmpty()) {
            return List.of();
        }

        List<String> envIds = saleEnvironmentList.stream().map(SaleEnvironment::getEnvId).toList();
        List<Long> requestIds = saleEnvironmentList.stream()
                .map(se -> se.getRequest().getReqId()).toList();

        Map<String, List<Order>> ordersMap = coreOrderService.getOrdersMapByEnvironmentIds(envIds);
        Map<Long, List<Product>> productsMap = coreProductService.getProductsMapByRequestIds(requestIds);

        // Load pricing totals per request
        List<Pricing> allPricings = pricingRepository.findByRequest_ReqIdIn(requestIds);
        Map<Long, List<Pricing>> pricingsMap = allPricings.stream()
                .collect(Collectors.groupingBy(p -> p.getRequest().getReqId()));

        return saleEnvironmentList.stream()
                .map(se -> toModel(se,
                        ordersMap.getOrDefault(se.getEnvId(), List.of()),
                        productsMap.getOrDefault(se.getRequest().getReqId(), List.of()),
                        pricingsMap.getOrDefault(se.getRequest().getReqId(), List.of())))
                .toList();
    }

    private SaleEnvironmentModel toModel(SaleEnvironment env, List<Order> orders, List<Product> products,
                                         List<Pricing> pricings) {
        SaleEnvironmentModel model = new SaleEnvironmentModel();
        model.setRequestUUID(env.getRequest().getReqUUID());
        model.setCreatedAt(Utils.formatTimeStamp(env.getCreatedAt()));
        model.setPublicLink(env.getPublicLink());
        model.setEnvState(env.isState()
                ? com.qrpublic.apartment.core.model.EnvStateEnum.ACTIVE
                : com.qrpublic.apartment.core.model.EnvStateEnum.INACTIVE);
        model.setOrders(orders.stream().map(this::toOrderModel).toList());
        model.setProducts(products.stream().map(this::toProductModel).toList());
        SellerModel sellerModel = new SellerModel();
        sellerModel.setUsername(env.getRequest().getSellerName());
        model.setSeller(sellerModel);

        // Set pricing totals
        if (!pricings.isEmpty()) {
            BigDecimal total = pricings.stream()
                    .map(Pricing::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.setTotalPrice(total);
            model.setCurrency(pricings.get(0).getCurrency());
        }

        return model;
    }

    private ProductModel toProductModel(Product p) {
        ProductModel m = new ProductModel();
        m.setProductName(p.getProductName());
        m.setQuantity(p.getAmount());
        m.setUnit(p.getUnit());
        m.setPrice(p.getPrice());
        m.setTotalQuantity(p.getTotal_amount());
        return m;
    }

    private OrderModel toOrderModel(Order o) {
        OrderModel m = new OrderModel();
        m.setBuyerName(o.getBuyerName());
        return m;
    }
}
