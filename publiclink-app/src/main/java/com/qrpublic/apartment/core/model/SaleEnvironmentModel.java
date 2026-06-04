package com.qrpublic.apartment.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class SaleEnvironmentModel {
    /**
     * request UUID
     */
    private String requestUUID;

    private String createdAt;
    private String plannedEndedAt;

    private SellerModel seller;
    private List<ProductModel> products;
    private List<OrderModel> orders;

    private String publicLink;
    private Date linkCreatedAt;
    private Date linkEndedAt;

    private EnvStateEnum envState;

    private BigDecimal totalPrice;
    private String currency;
}
