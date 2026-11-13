package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Yoga view of one sale environment, resolved from a public link token or
 * envId. Entities never leave the core layer; the domain works with this DTO.
 */
@Data
@Builder
public class YogaEnvModel {
    private String envId;
    private String requestUUID;
    /**
     * the sale environment public link token; the yoga endpoints take it as token
     */
    private String publicLink;
    private String sellerUsername;
    private boolean active;
    /** epoch millis of sale_environment.will_ended_at (precise lifetime signal) */
    private Long willEndedAtMillis;
    private String productType;
    private String productName;
    /** drop-in price per class for pay-as-you-go buyers */
    private BigDecimal dropInPrice;
}
