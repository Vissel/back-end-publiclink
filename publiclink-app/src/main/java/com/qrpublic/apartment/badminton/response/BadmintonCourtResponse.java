package com.qrpublic.apartment.badminton.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Exact seller court model; internal environment ids are not exposed. */
@Data
@Builder
public class BadmintonCourtResponse {
    private String courtCode;
    private String courtName;
    private boolean active;
    private List<BadmintonCourtScheduleResponse> schedules;
}
