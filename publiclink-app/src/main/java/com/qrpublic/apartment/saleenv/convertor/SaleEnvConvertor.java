package com.qrpublic.apartment.saleenv.convertor;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.model.EnvStateEnum;
import com.qrpublic.apartment.core.model.SaleEnvironmentModel;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.model.convertor.OrderConvertor;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class SaleEnvConvertor {
    public static SaleEnvDTO.SaleEnvDTOBuilder buildEnvDTO(SaleEnvironment env) {
        Request req = org.hibernate.Hibernate.isInitialized(env.getRequest()) ? env.getRequest() : null;
        String productName = CommonConstant.EMPTY;
        if (req != null && org.hibernate.Hibernate.isInitialized(req.getProducts()) && !req.getProducts().isEmpty()) {
            productName = req.getProducts().getFirst().getProductName();
        }
        List<OrderDTO> orders = org.hibernate.Hibernate.isInitialized(env.getListOrder())
                ? createListOrderDTO(env.getListOrder()) : List.of();

        return SaleEnvDTO.builder()
                .createdAt(Utils.formatTimeStamp(env.getCreatedAt()))
                .sellerName(req != null ? req.getSellerName() : null)
                .productName(productName)
                .publicLink(env.getPublicLink())
                .createdBy(req != null && req.getCreatedBy() != null ? req.getCreatedBy().getName() : null)
                .envStatus(env.isState())
                .orders(orders)
                .requestUUID(req != null ? req.getReqUUID() : null)
                ;
    }


    private static List<OrderDTO> createListOrderDTO(List<com.qrpublic.apartment.entity.Order> listOrder) {
        List<OrderDTO> orderDTOs = new ArrayList<>();
        if (listOrder != null && !listOrder.isEmpty()) {
            String publicLink = listOrder.get(0).getSaleEnvironment().getPublicLink();
            listOrder.stream().forEach(o -> orderDTOs.add(OrderConvertor.createOrderDTO(o, o.getOrderedAt().getTime(), publicLink)));
        }
        return orderDTOs;
    }

    public static SaleEnvDTO buildSaleEnvDTOFromModel(SaleEnvironmentModel model) {
        List<OrderDTO> orders = model.getOrders() == null ? List.of() :
                model.getOrders().stream()
                .map(o ->
                     new OrderDTO(0, null, o.getBuyerName(), null, false, false, null, 0, null, null)
                ).toList();
        final String sellerAuthLink = LinkBuilder.buildAuthenticationLink(model.getRequestUUID(), model.getSeller().getSellerLinkModel().getContextString());
        final String publicLink = LinkBuilder.buildPublicLink(model.getPublicLink());
        return SaleEnvDTO.builder()
                .requestUUID(model.getRequestUUID())
                .createdAt(model.getCreatedAt())
                .sellerName(model.getSeller() != null ? model.getSeller().getName() : null)
                .createdBy(model.getSeller() != null ? model.getSeller().getUsername() : null)
                .sellerAuthLink(sellerAuthLink)
                .publicLink(publicLink)
                .envStatus(model.getEnvState() == EnvStateEnum.ACTIVE)
                .productName(model.getProducts() != null && !model.getProducts().isEmpty() ? model.getProducts().getFirst().getProductName() : null)
                .orders(orders).build();
    }
}
