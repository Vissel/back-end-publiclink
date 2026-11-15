package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Re-issue the payment URL of the selected assignments. Used when a seller
 * lost the link or the old one passed its validity: the payment record itself
 * stays untouched, only its token is rebuilt.
 */
@Data
public class RegenerateLinkRequest {
    private List<String> planUserCodes;
}
