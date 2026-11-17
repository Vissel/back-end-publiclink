package com.qrpublic.apartment.yoga.request;

import lombok.Data;

@Data
public class YogaCancelRequest {
    private String token;
    private String bookingCode;
}
