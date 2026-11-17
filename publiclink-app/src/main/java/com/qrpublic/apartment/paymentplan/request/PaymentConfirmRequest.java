package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Seller pressed "Confirm payment" after transferring. Contact info lets the
 * platform promise a result via chat / email / phone.
 */
@Data
public class PaymentConfirmRequest {
    private String token;
    private String email;
    private String phone;
}
