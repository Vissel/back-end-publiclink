package com.qrpublic.apartment.saleenv.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Seller edit of an already created sale environment: the closing time may be
 * moved earlier, the sale closed immediately, or pushed later as far as the
 * paid plan covers. A later end is bounded by the plan because the public link
 * is re-minted for it, so the link keeps expiring exactly when the sale does;
 * it also means the public address of the sale changes.
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
