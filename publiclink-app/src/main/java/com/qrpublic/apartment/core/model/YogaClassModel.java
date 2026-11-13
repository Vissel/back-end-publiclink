package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * Weekly yoga class template as exchanged above the core layer. Times are
 * "HH:mm" 24h strings; dayOfWeek is ISO (1 = Monday .. 7 = Sunday).
 */
@Data
@Builder
public class YogaClassModel {
    private String classCode;
    /** owning environment id; ownership checks compare against it */
    private String envId;
    private int dayOfWeek;
    private String startTime;
    private String endTime;
    private String title;
    private String coach;
    private int capacity;
    /** null on create defaults to active; set explicitly on update */
    private Boolean active;
}
