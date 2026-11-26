package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/**
 * Reserve one frame; buyer identity comes only from the forwarded JWT header.
 */
@Data
public class BadmintonBookRequest {
    private String token;
    private String courtCode;
    private String date;
    private String startTime;
}
