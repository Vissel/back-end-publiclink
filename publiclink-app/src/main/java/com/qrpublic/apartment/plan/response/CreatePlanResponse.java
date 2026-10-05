package com.qrpublic.apartment.plan.response;

import lombok.Data;

@Data
public class CreatePlanResponse {
    private String requestUuid;
    private String sellerUsername;
    private String endTime;
    private String status;
}