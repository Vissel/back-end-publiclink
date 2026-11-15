package com.qrpublic.apartment.saleenv.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Attach (or detach) the transfer account shown on a sale page. The account
 * must belong to the caller: the seller shows one of their own, an admin one
 * of the admin's own. An empty transferCode removes the account from the page.
 */
@Data
public class UpdateEnvironmentTransferRequest {
    /** request UUID of the environment, as carried by the sale page. */
    @NotBlank
    private String requestUuid;

    /** transfer_info.transfer_code of the caller's account; blank to detach. */
    private String transferCode;
}
