package com.qrpublic.apartment.product.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Product data from the frontend's combined createSaleEnvironment payload.
 * <p>
 * JSON shape:
 *
 * <pre>
 * "product": {
 *   "productName": "Widget",
 *   "description": "...",
 *   "amount": 5,
 *   "price": 100000,
 *   "images": ["data:image/png;base64,...", "data:image/jpeg;base64,..."]
 * }
 * </pre>
 */
@Data
public class CreateProductRequest {

    @JsonProperty("productName")
    private String name;

    private String description;

    @JsonProperty("totalAmount")
    private int quantity;

    private double price;

    /**
     * STANDARD (default) or YOGA_BOOKING. Chosen by the seller/admin in the
     * generate form; drives whether the public page renders the order form or
     * the yoga calendar.
     */
    @JsonProperty("productType")
    private String productType;

    /**
     * Raw base64 data URIs sent by the frontend.
     * Each string is like {@code "data:image/png;base64,iVBORw0KG..."}.
     * Convert to {@link CreateProductImageRequest} objects in the service layer.
     */
    @JsonProperty("images")
    private List<String> imageDataList;
}
