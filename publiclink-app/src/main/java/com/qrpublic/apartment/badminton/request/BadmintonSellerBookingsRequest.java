package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Seller booking list; omitted date returns all bookings on the page. */
@Data
public class BadmintonSellerBookingsRequest {
    private String token;
    private String date;
}
