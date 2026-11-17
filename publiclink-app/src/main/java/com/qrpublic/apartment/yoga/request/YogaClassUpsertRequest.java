package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Seller create/update of a weekly class. Times are HH:mm 24h. */
@Data
public class YogaClassUpsertRequest {
    private String token;
    /** null on create, set on update */
    private String classCode;
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;
    private String title;
    private String coach;
    private Integer capacity;
    private Boolean active;
}
