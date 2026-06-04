package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleEnvDTO {
    private String createdAt;
    private String plannedEndedAt;
    private String sellerName;
    private String sellerLink;
    /**
     * the link for seller while 1st time accessing sale environment
     */
    private String sellerAuthLink;
    /**
     * expired of seller authentication link
     */
    private Date sellerAuthLinkExpire;

    /**
     * request UUID
     */
    private String requestUUID;

    private String productName;
    private String publicLink;
    private String createdBy;
    private boolean envStatus;
    private String requestId;
    private List<OrderDTO> orders;
    private BigDecimal totalPrice;
    private String currency;
    private List<PricingDTO> pricings;

    // dont have sellerAuthLink
    public SaleEnvDTO(String createdAt, String sellerName, String sellerLink, String productName, String publicLink,
                      String createdBy, boolean envStatus, List<OrderDTO> orders) {
        super();
        this.createdAt = createdAt;
        this.sellerName = sellerName;
        this.sellerLink = sellerLink;
        this.productName = productName;
        this.publicLink = publicLink;
        this.createdBy = createdBy;
        this.envStatus = envStatus;
        this.orders = orders;
    }

}
