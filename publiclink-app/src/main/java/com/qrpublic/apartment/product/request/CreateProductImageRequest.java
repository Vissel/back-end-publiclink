package com.qrpublic.apartment.product.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single product image sent from the frontend.
 * Each element in the {@code "images"} array of the combined
 * {@code createSaleEnvironment} payload is a base64 data URI,
 * e.g. {@code "data:image/png;base64,iVBORw0KG..."}.
 * <p>
 * The service layer can parse the data URI to extract
 * the MIME type and the raw base64 content for persistence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductImageRequest {

    /**
     * Full base64 data URI of the image.
     * Format: {@code data:image/{type};base64,{encodedData}}
     */
    private String imageData;
}
