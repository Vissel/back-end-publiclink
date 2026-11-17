package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Seller marks a booking paid and/or attended. */
@Data
public class YogaBookingToggleRequest {
    private String token;
    private String bookingCode;
    private Boolean paid;
    private Boolean attended;
}
