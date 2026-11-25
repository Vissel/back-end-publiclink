package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Court and weekly schedules above the core layer. */
@Data
@Builder
public class BadmintonCourtModel {
    private String courtCode;
    /** Internal ownership key; API response DTOs deliberately omit it. */
    private String envId;
    private String courtName;
    private Boolean active;
    private List<BadmintonCourtScheduleModel> schedules;
}
