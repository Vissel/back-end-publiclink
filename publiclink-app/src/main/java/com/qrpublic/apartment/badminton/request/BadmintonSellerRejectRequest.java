package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Seller rejection payload with an optional buyer-visible note. */
@Data
public class BadmintonSellerRejectRequest {
    private String token;
    private String bookingCode;
    private String note;
}
