package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/**
 * One concrete occurrence lookup (class code + yyyy-MM-dd date) above a
 * public link token. Used for the seller buyer list behind a calendar cell.
 */
@Data
public class YogaOccurrenceRequest {
    private String token;
    private String classCode;
    private String date;
}
