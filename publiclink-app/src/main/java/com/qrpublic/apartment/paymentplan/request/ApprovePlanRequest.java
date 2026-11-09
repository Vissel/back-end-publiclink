package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

import java.util.List;

/**
 * Multi-select approve: CREATE -> ACTIVE with the admin picked active date.
 * activeDate format: yyyy-MM-dd (or yyyy-MM-dd HH:mm:ss).
 */
@Data
public class ApprovePlanRequest {
    private List<String> planUserCodes;
    private String activeDate;
}
