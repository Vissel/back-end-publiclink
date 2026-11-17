package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Filter payload for the setup page tables. search / filter / pagination share
 * this one request body, only the fields differ.
 * state: ACTIVE (default when null) | CREATE | DEACTIVE | ALL
 */
@Data
public class ListPlanUserRequest {
    private String state;
    private String username;
}
