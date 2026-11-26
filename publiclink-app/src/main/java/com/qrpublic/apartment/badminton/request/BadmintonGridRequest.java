package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Public court-grid request for one concrete date. */
@Data
public class BadmintonGridRequest {
    private String token;
    private String date;
}
