package com.qrpublic.apartment.saleenv.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Seller edit of an already created sale environment: the closing time may be
 * moved earlier ("close the sale now / sooner") but never later, because the
 * public link token was minted to expire at the original end and cannot be
 * lengthened without regenerating it. The plan-end constraint therefore stays
 * satisfied on its own.
 */
@Data
public class UpdateEnvironmentWindowRequest {
    /** request UUID of the environment, as returned by the seller list. */
    @NotBlank
    private String requestUuid;

    /** New closing instant, epoch millis. */
    @NotNull
    private Long endAt;
}
