package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Public payment page input: the token carried by the payment URL.
 */
@Data
public class PaymentInfoRequest {
    private String token;
}
