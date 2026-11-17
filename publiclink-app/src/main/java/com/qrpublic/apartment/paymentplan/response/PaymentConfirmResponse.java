package com.qrpublic.apartment.paymentplan.response;

import lombok.Data;

/**
 * Answer of the seller's "Confirm payment" click.
 */
@Data
public class PaymentConfirmResponse {
    private String status;
    /** guidance text the page shows after confirming */
    private String message;
}
