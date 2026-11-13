package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Booking payload. date is the concrete occurrence yyyy-MM-dd. */
@Data
public class YogaBookRequest {
    private String token;
    private String classCode;
    private String date;
    /** required for pay-as-you-go walk-ins */
    private String buyerName;
    /** seller mode: book on behalf of this plan buyer, no password needed */
    private String buyerUsername;
    /** optional PAY_AS_YOU_GO override for a plan buyer */
    private String paymentMode;
}
