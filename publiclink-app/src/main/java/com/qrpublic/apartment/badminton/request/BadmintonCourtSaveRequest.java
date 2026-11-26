package com.qrpublic.apartment.badminton.request;

import lombok.Data;

import java.util.List;

/** Seller create/update payload; omitted courtCode creates a new court. */
@Data
public class BadmintonCourtSaveRequest {
    private String token;
    private String courtCode;
    private String courtName;
    private List<BadmintonCourtScheduleRequest> schedules;
}
