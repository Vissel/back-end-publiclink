package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Seller court-list payload, including inactive courts. */
@Data
public class BadmintonSellerCourtsRequest {
    private String token;
}
