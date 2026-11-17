package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * One concrete weekly occurrence (class on a date) with its live slot
 * accounting. Used by the calendar grid and the below-calendar class list.
 */
@Data
@Builder
public class YogaOccurrenceModel {
    private String classCode;
    private String date;
    private int dayOfWeek;
    private String startTime;
    private String endTime;
    private String title;
    private String coach;
    private int capacity;
    private int booked;
    private int remaining;
    /** true when the occurrence date is in the past (DB time) */
    private boolean past;
    /** booking code when the requesting buyer already holds a slot */
    private String myBookingCode;
    private String myPaymentMode;
}
