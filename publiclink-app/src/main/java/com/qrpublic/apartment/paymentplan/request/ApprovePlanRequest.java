package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Multi-select approve: CREATE -> ACTIVE from the start the admin gives here.
 * startAt format: yyyy-MM-dd'T'HH:mm (yyyy-MM-dd and yyyy-MM-dd HH:mm:ss are
 * accepted too). Blank means "use the start the seller asked for, or today".
 */
@Data
public class ApprovePlanRequest {
    private List<String> planUserCodes;
    private String startAt;
}