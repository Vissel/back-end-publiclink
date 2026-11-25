package com.qrpublic.apartment.badminton.request;

import lombok.Data;

/** Token-only request for the caller's booking list. */
@Data
public class BadmintonTokenRequest {
    private String token;
}
