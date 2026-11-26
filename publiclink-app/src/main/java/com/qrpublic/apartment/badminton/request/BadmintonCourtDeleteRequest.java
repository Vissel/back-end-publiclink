package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Seller soft-delete payload. */
@Data
public class BadmintonCourtDeleteRequest {
    private String token;
    private String courtCode;
}
