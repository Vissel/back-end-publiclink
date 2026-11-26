package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/**
 * Shared token + booking-code payload for payment confirmation and
 * cancellation.
 */
@Data
public class BadmintonBookingCodeRequest {
    private String token;
    private String bookingCode;
}
