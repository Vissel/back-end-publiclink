package com.qrpublic.apartment.paymentplan.response;

import com.qrpublic.apartment.core.model.PlanUserModel;
import lombok.Data;

import java.util.List;

/**
 * What the "More plan" screen shows in one payload: what the account already
 * pays for, and the single order it still has to transfer.
 *
 * <p>
 * Unlike the onboarding state there is deliberately no terminal {@code ACTIVE}
 * here. An account that already runs a paid plan is exactly the account that
 * comes to this screen, so the catalog and the extension form stay available
 * whenever nothing is waiting to be paid.
 */
@Data
public class MorePlanStateResponse {

    /** No open order: buying another plan or adding days are both possible. */
    public static final String STATE_READY = "READY";
    /** An order is open and the transfer still has to be declared. */
    public static final String STATE_WAITING_TRANSFER = "WAITING_TRANSFER";
    /** Transfer declared, waiting for the admin to verify the money. */
    public static final String STATE_WAITING_CONFIRM = "WAITING_CONFIRM";

    private String state;

    /**
     * Email the account gave at registration; prefills the payment confirmation.
     */
    private String contactEmail;

    /** Product links still creatable across the plans that are running. */
    private int remainingQuota;

    /**
     * Latest instant the account has paid for; null when nothing reaches the
     * future.
     */
    private String coverageEndDate;

    /**
     * ACTIVE assignments, newest first; these are the plans that can be extended.
     */
    private List<PlanUserModel> activePlans;

    /**
     * Open order with its transfer instructions, null when there is nothing to pay.
     */
    private PaymentInfoResponse pendingPayment;
}
