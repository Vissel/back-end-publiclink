package com.qrpublic.apartment.paymentplan.request;

import lombok.Data;

/**
 * Asks the backend what window a plan would run for, so the seller sees the
 * dates before committing. A blank start means "from now"; an explicit one is
 * the seller moving the start and getting the matching end back.
 */
@Data
public class PlanWindowRequest {

    private String planCode;

    /**
     * yyyy-MM-dd'T'HH:mm in the seller's own time zone, the same string the
     * date picker on the plan screen produces. Optional.
     */
    private String startAt;
}