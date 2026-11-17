package com.qrpublic.apartment.paymentplan.response;

import com.qrpublic.apartment.core.model.PlanUserModel;
import lombok.Data;

/**
 * Where a seller stands in the self-service onboarding, in one payload: the
 * plan screen reads it to decide which step to show, and the same shape comes
 * back from the plan pick so the screen never has to guess.
 */
@Data
public class SelfServiceStateResponse {

    /** Nothing bought yet: show the catalog. */
    public static final String STATE_PICK_PLAN = "PICK_PLAN";
    /** An order is open and the transfer still has to be declared. */
    public static final String STATE_WAITING_TRANSFER = "WAITING_TRANSFER";
    /** Transfer declared, waiting for the admin to verify the money. */
    public static final String STATE_WAITING_CONFIRM = "WAITING_CONFIRM";
    /** A paid plan is running with quota left. */
    public static final String STATE_ACTIVE = "ACTIVE";

    private String state;

    /** Email the seller gave at registration; prefills the payment confirmation. */
    private String contactEmail;

    /** Newest assignment that is ACTIVE; null when nothing is paid for yet. */
    private PlanUserModel activePlan;

    /** Open order of the seller, with the transfer details to pay by. */
    private PaymentInfoResponse pendingPayment;
}
