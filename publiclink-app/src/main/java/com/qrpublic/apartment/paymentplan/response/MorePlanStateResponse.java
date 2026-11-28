package com.qrpublic.apartment.paymentplan.response;

import com.qrpublic.apartment.core.model.PlanUserModel;
import lombok.Data;

import java.math.BigDecimal;
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

    /**
     * PRODUCT or BOOKING, the purpose this account registered for. The buy tab
     * filters its catalog with it, and a conversion never leaves it.
     */
    private String planPurpose;

    /**
     * True once this account has ever been granted a 0 fee plan. Buying more is
     * not a second chance at the free trial, and a conversion can never land on
     * one either, so the screen greys free plans out on both tabs.
     */
    private boolean freePlanUsed;

    /**
     * Present when this answer came from a conversion request: what happened and
     * how much, so the screen can show the payment it opened or the success it
     * owed without an extra round trip.
     */
    private ConversionOutcome conversionOutcome;

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

    /**
     * Result of a conversion request.
     */
    @Data
    public static class ConversionOutcome {
        /** An order was opened for the difference and the transfer is still due. */
        public static final String ORDER_CREATED = "ORDER_CREATED";
        /**
         * The difference was zero or negative, so nothing is charged: the plan
         * was converted on the spot and the surplus is forfeited.
         */
        public static final String NO_PAYMENT = "NO_PAYMENT";

        /** ORDER_CREATED | NO_PAYMENT */
        private String result;
        /** new plan fee minus the fee already paid; negative means forfeited */
        private BigDecimal feeDelta;
        private String sourcePlanUserCode;
        private String conversionPlanUserCode;
        private String targetPlanCode;
        private BigDecimal targetFee;
        private int targetPlanDay;
        private int targetEnvCount;
        /** the converted plan's window starts again from this date ... */
        private String newActiveDate;
        /** ... and runs to this one */
        private String newEndedDate;
    }
}
