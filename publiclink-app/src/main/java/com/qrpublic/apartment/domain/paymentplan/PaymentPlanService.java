package com.qrpublic.apartment.domain.paymentplan;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.model.LinkModel;
import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CorePaymentPlanService;
import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.paymentplan.request.CreateCatalogPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ConvertPlanResponse;
import com.qrpublic.apartment.paymentplan.response.CreatedPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ExtendPlanResponse;
import com.qrpublic.apartment.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the payment plan feature. Owns the transaction boundary
 * for the create-assignment flow and calls down to the core layer plus
 * LinkService to build the payment URL. No DB ids travel in or out; only
 * usernames and codes.
 */
@Slf4j
@Component
public class PaymentPlanService {

    @Autowired
    private CorePaymentPlanService corePaymentPlanService;

    @Autowired
    private CorePaymentService corePaymentService;

    /**
     * Tells the seller their money was accepted and the plan is running; the
     * self-service screen polls for exactly this moment.
     */
    @Autowired
    private com.qrpublic.apartment.core.service.CoreNotificationService coreNotificationService;

    @Autowired
    private LinkService linkService;

    @Value("${payment.link.expiry}")
    private long paymentLinkExpiryMillis;

    /**
     * Create a catalog plan on its own. No seller is involved and no payment is
     * produced; linking it to a seller is the separate assignSeller step.
     */
    @Transactional
    public CreatedPlanResponse createCatalogPlan(CreateCatalogPlanRequest request) {
        int envCount = request.getEnvCount() > 0 ? request.getEnvCount() : 1;
        com.qrpublic.apartment.core.model.PaymentPlanModel plan = corePaymentPlanService.createPlan(
                request.getFee(), request.getPlanDay(), envCount, request.getDescription(),
                request.getPlanGroup());

        CreatedPlanResponse response = new CreatedPlanResponse();
        response.setPlanCode(plan.getPlanCode());
        response.setFee(plan.getFee());
        response.setPlanDay(plan.getPlanDay());
        response.setEnvCount(plan.getEnvCount());
        response.setDescription(plan.getDescription());
        response.setPlanState(plan.getPlanState());
        response.setPlanGroup(plan.getPlanGroup());
        return response;
    }

    /**
     * Link an existing plan to a seller: creates the assignment plus the payment
     * record in CREATE state, generates the payment URL and returns it for the
     * admin to send manually. Single transaction so assignment and payment are
     * always consistent.
     *
     * @param adminUsername authenticated admin (X-User-ID); the payment page
     *                      shows this admin's transfer accounts
     */
    @Transactional
    public CreateSellerPlanResponse createSellerPlan(CreateSellerPlanRequest request, String adminUsername) {
        PlanUserModel assignment = corePaymentPlanService.createAssignment(
                request.getUsername(), request.getPlanCode());

        BigDecimal fee = assignment.getFee();
        PaymentModel payment = corePaymentService.createPayment(
                assignment.getPlanUserCode(), fee, adminUsername);

        String paymentUrl = buildPaymentUrl(payment.getPaymentCode());
        corePaymentService.updatePaymentUrl(payment.getPaymentCode(), paymentUrl);

        log.info("Created plan assignment [{}] and payment [{}] for seller [{}]",
                assignment.getPlanUserCode(), payment.getPaymentCode(), request.getUsername());

        CreateSellerPlanResponse response = new CreateSellerPlanResponse();
        response.setPaymentCode(payment.getPaymentCode());
        response.setPaymentUrl(paymentUrl);
        response.setSellerUsername(request.getUsername());
        response.setFee(fee);
        response.setPlanDay(assignment.getPlanDay());
        response.setEnvCount(assignment.getEnvQuota());
        response.setStatus(payment.getStatus());
        return response;
    }

    /**
     * Remaining environment quota for the seller across ACTIVE assignments.
     */
    @Transactional(readOnly = true)
    public int remainingQuota(String username) {
        return corePaymentPlanService.findRemainingQuota(username);
    }

    /**
     * Remaining quota of one purpose group, which is what the onboarding guard
     * needs: a running plan of the other group does not cover this purchase.
     */
    @Transactional(readOnly = true)
    public int remainingQuota(String username, String planGroup) {
        return corePaymentPlanService.findRemainingQuota(username, planGroup);
    }

    @Transactional(readOnly = true)
    public List<PlanUserModel> activeAssignments(String username) {
        return corePaymentPlanService.findActiveAssignments(username);
    }

    /**
     * Every assignment the seller owns, newest first, each carrying its latest
     * payment code, status and URL. This is what the self-service screen reads
     * to decide between "pick a plan", "transfer waiting" and "plan active".
     */
    @Transactional(readOnly = true)
    public List<PlanUserModel> assignmentsWithPayments(String username) {
        List<PlanUserModel> assignments = corePaymentPlanService.findAllAssignments(username);
        enrichWithPayment(assignments);
        return assignments;
    }

    /**
     * ACTIVE plan catalog for the select-plan dropdown.
     */
    @Transactional(readOnly = true)
    public List<com.qrpublic.apartment.core.model.PaymentPlanModel> catalog() {
        return corePaymentPlanService.findActivePlans();
    }

    /**
     * Catalog of one purpose group only, for the self-service screens.
     */
    @Transactional(readOnly = true)
    public List<com.qrpublic.apartment.core.model.PaymentPlanModel> catalog(String planGroup) {
        return corePaymentPlanService.findActivePlansByGroup(planGroup);
    }

    /**
     * Setup table page. {@code page} is the 1-based page sent by the UI, so it
     * is converted to the zero-based Spring page here.
     */
    @Transactional(readOnly = true)
    public List<PlanUserModel> listAssignments(String state, String username, int page, int size) {
        return corePaymentPlanService.findAssignments(state, username,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.max(1, size),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("createdAt"))));
    }

    @Transactional(readOnly = true)
    public long countAssignments(String state, String username) {
        return corePaymentPlanService.countAssignments(state, username);
    }

    /**
     * Attach the latest payment code/status/URL to each assignment model (used
     * by the setup tables, the mark-paid action and the copy-link action).
     */
    @Transactional(readOnly = true)
    public void enrichWithPayment(List<PlanUserModel> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        List<String> codes = assignments.stream().map(PlanUserModel::getPlanUserCode).toList();
        Map<String, PaymentModel> latestByCode = latestPaymentsByAssignment(codes);
        assignments.forEach(a -> {
            PaymentModel payment = latestByCode.get(a.getPlanUserCode());
            if (payment != null) {
                a.setPaymentCode(payment.getPaymentCode());
                a.setPaymentStatus(payment.getStatus());
                a.setPaymentUrl(payment.getPaymentUrl());
                a.setCreatedBy(payment.getCreatedBy());
            }
        });
    }

    /**
     * Newest payment of every selected assignment. The core lookup comes back
     * ordered by createdAt DESC, so the first entry seen per code is the latest.
     */
    private Map<String, PaymentModel> latestPaymentsByAssignment(List<String> planUserCodes) {
        Map<String, PaymentModel> latestByCode = new HashMap<>();
        for (PaymentModel payment : corePaymentService.findPaymentsByPlanUserCodes(planUserCodes)) {
            latestByCode.putIfAbsent(payment.getPlanUserCode(), payment);
        }
        return latestByCode;
    }

    /**
     * Approve: CREATE -> ACTIVE for every selected code, activation date chosen
     * by admin, end date derived from the plan length.
     */
    @Transactional
    public int approve(List<String> planUserCodes, String activeDate) {
        int count = 0;
        for (String code : planUserCodes) {
            corePaymentPlanService.activate(code, activeDate);
            count++;
        }
        log.info("Approved {} plan assignment(s) with active date [{}]", count, activeDate);
        return count;
    }

    /**
     * Reject / deactivate: -> DEACTIVE for every selected code.
     */
    @Transactional
    public int reject(List<String> planUserCodes) {
        int count = 0;
        for (String code : planUserCodes) {
            corePaymentPlanService.deactivate(code);
            count++;
        }
        log.info("Rejected/deactivated {} plan assignment(s)", count);
        return count;
    }

    /**
     * Read-only gate used by the UI before it opens the product screen. Reports
     * the plan window and the reason a creation would be refused.
     */
    @Transactional(readOnly = true)
    public com.qrpublic.apartment.core.model.PlanGateModel evaluate(String username) {
        return corePaymentPlanService.evaluate(username);
    }

    /**
     * Gate evaluated against one purpose group, so the answer is about the link
     * being created and not merely about the account owning some plan.
     */
    @Transactional(readOnly = true)
    public com.qrpublic.apartment.core.model.PlanGateModel evaluate(String username, String planGroup) {
        return corePaymentPlanService.evaluate(username, planGroup);
    }

    /**
     * Latest instant the seller has paid for, quota ignored, or {@code null}
     * when no plan reaches the future. This is the ceiling for stretching a
     * sale window that already exists: running an existing link longer spends
     * no new slot, so days bought by a later plan or an extension order count
     * here even though they cannot be used to create a new environment.
     */
    @Transactional(readOnly = true)
    public java.util.Date planCoverageEnd(String username) {
        return corePaymentPlanService.findPaidCoverageEnd(username);
    }

    /**
     * Consume one environment slot for the seller. Called inside the sale
     * environment transaction: refuses creation when the plan has not started,
     * has ended, or has no slot left, and returns the plan deadline so the
     * generated link can be clamped to it.
     */
    @Transactional
    public com.qrpublic.apartment.core.model.PlanGateModel consumeQuotaOrThrow(String username) {
        return consumeQuotaOrThrow(username, null);
    }

    /**
     * Consume one slot of a specific purpose group. A blank group keeps the old
     * any-plan behaviour, which is what a flow that creates no typed product uses.
     */
    @Transactional
    public com.qrpublic.apartment.core.model.PlanGateModel consumeQuotaOrThrow(String username, String planGroup) {
        com.qrpublic.apartment.core.model.PlanGateModel gate = corePaymentPlanService.consumeOne(username, planGroup);
        if (!gate.isAllowed()) {
            log.warn("Refused environment creation for [{}]: {}", username, gate.getReason());
            throw new ApplicationException(gate.getMessage(), 403);
        }
        return gate;
    }

    /**
     * Admin confirms the money arrived for every selected assignment: each
     * payment goes to PAID and a bill is created, and the paid plan starts
     * running right away so it leaves the "waiting for pay" queue and shows up
     * as an active plan. Only a CREATE assignment is started: re-paying an
     * already ACTIVE one must not silently shift its window, and a DEACTIVE one
     * stays deactivated until the admin activates it deliberately.
     * An extension order never becomes a plan of its own: its days are added to
     * the plan it extends instead.
     * Already paid payments are left untouched but still counted.
     */
    @Transactional
    public int markPaidAssignments(List<String> planUserCodes, String note, String adminUsername,
            String activeDate) {
        Map<String, PaymentModel> latest = latestPaymentsByAssignment(planUserCodes);
        int count = 0;
        int started = 0;
        int extended = 0;
        int converted = 0;
        for (String planUserCode : planUserCodes) {
            corePaymentService.markPaid(requirePayment(latest, planUserCode).getPaymentCode(),
                    note, adminUsername);
            count++;
            PlanUserModel assignment = corePaymentPlanService.findAssignmentByCode(planUserCode);
            if (!"CREATE".equals(assignment.getPlanState())) {
                continue;
            }
            if (assignment.getConvertsPlanUserCode() != null) {
                // A conversion order never becomes a plan of its own: the plan it
                // converts is rebuilt onto the target instead.
                PlanUserModel rebuilt = corePaymentPlanService.applyConversion(planUserCode);
                converted++;
                notifyPlanConverted(assignment.getUsername(), rebuilt, adminUsername);
            } else if (assignment.getExtendDays() != null) {
                corePaymentPlanService.applyExtension(planUserCode);
                extended++;
            } else {
                PlanUserModel startedPlan = corePaymentPlanService.activate(planUserCode, activeDate);
                started++;
                notifyPlanActivated(assignment.getUsername(), startedPlan, adminUsername);
            }
        }
        log.info("Marked {} payment(s) as paid by admin [{}]: {} plan(s) started on [{}], {} plan(s) extended, "
                + "{} plan(s) converted", count, adminUsername, started, activeDate, extended, converted);
        return count;
    }

    /**
     * The money for a conversion arrived and the plan became the better one. The
     * window restarted, so the seller has to be told the new dates rather than
     * left reading the plan they used to have.
     */
    private void notifyPlanConverted(String username, PlanUserModel plan, String adminUsername) {
        if (username == null || username.isBlank()) {
            return;
        }
        String message = "Your plan was upgraded to " + plan.getPlanDay() + " day(s) with "
                + plan.getEnvQuota() + " link(s), active from " + plan.getActiveDate()
                + " to " + plan.getEndedDate() + ".";
        try {
            coreNotificationService.notifyUsernames(List.of(username), "Plan converted", message,
                    "PLAN", adminUsername, "PLAN", plan.getPlanUserCode());
        } catch (RuntimeException e) {
            log.error("Converted plan for [{}] but the notification failed: {}", username, e.getMessage());
        }
    }

    /**
     * The seller paid and the admin confirmed the money: say so, because the
     * onboarding screen is sitting on this news. A notification failure must
     * not undo a paid plan, so it is reported and swallowed.
     */
    private void notifyPlanActivated(String username, PlanUserModel plan, String adminUsername) {
        if (username == null || username.isBlank()) {
            return;
        }
        String message = "Your plan is active from " + plan.getActiveDate() + " to " + plan.getEndedDate()
                + " with " + plan.getEnvQuota() + " link(s). You can start selling now.";
        try {
            coreNotificationService.notifyUsernames(List.of(username), "Plan activated", message,
                    "PLAN", adminUsername, "PLAN", plan.getPlanUserCode());
        } catch (RuntimeException e) {
            log.error("Plan activated for [{}] but the notification failed: {}", username, e.getMessage());
        }
    }

    /**
     * Sell extra days on a plan that is already running: an extension order
     * carrying no environment quota of its own, plus its payment and link,
     * priced at the plan's daily rate. The extended plan only gains the days
     * once the order is marked paid, exactly like a plan purchase.
     *
     * @param planUserCode the ACTIVE assignment to extend
     * @param days         days to add, at least one
     */
    @Transactional
    public ExtendPlanResponse createExtension(String planUserCode, int days, String adminUsername) {
        PlanUserModel order = corePaymentPlanService.createExtensionAssignment(planUserCode, days);
        PaymentModel payment = corePaymentService.createPayment(
                order.getPlanUserCode(), order.getFee(), adminUsername);
        String paymentUrl = buildPaymentUrl(payment.getPaymentCode());
        corePaymentService.updatePaymentUrl(payment.getPaymentCode(), paymentUrl);

        ExtendPlanResponse response = new ExtendPlanResponse();
        response.setPlanUserCode(planUserCode);
        response.setExtensionPlanUserCode(order.getPlanUserCode());
        response.setSellerUsername(order.getUsername());
        response.setDays(days);
        response.setFee(order.getFee());
        response.setPlanDay(order.getPlanDay());
        // The order row holds the projected window: its start is the end date
        // the plan has right now, or today when the plan already ran out.
        response.setCurrentEndDate(order.getActiveDate());
        response.setExtendedEndDate(order.getEndedDate());
        response.setPaymentCode(payment.getPaymentCode());
        response.setPaymentUrl(paymentUrl);
        response.setStatus(payment.getStatus());
        return response;
    }

    /**
     * Payment page URL for a payment code: a fresh secured link holding the
     * code, valid for {@code payment.link.expiry}. Shared by the plan purchase,
     * the extension order and the regeneration, so all three hand out the same
     * kind of URL.
     */
    /**
     * Move a running plan onto a dearer one of the same purpose group for only
     * the difference: a conversion order that grants no environment slot, plus
     * its payment priced at that difference. The converted plan is rebuilt when
     * the order is marked paid, exactly like an extension waits for its money.
     */
    @Transactional
    public ConvertPlanResponse createConversion(String sourcePlanUserCode, String targetPlanCode,
            String adminUsername) {
        PlanUserModel order = corePaymentPlanService.createConversionAssignment(sourcePlanUserCode,
                targetPlanCode);
        // order.getFee() is the stored delta, not the target's full price.
        PaymentModel payment = corePaymentService.createPayment(
                order.getPlanUserCode(), order.getFee(), adminUsername);
        String paymentUrl = buildPaymentUrl(payment.getPaymentCode());
        corePaymentService.updatePaymentUrl(payment.getPaymentCode(), paymentUrl);

        ConvertPlanResponse response = new ConvertPlanResponse();
        response.setSourcePlanUserCode(sourcePlanUserCode);
        response.setConversionPlanUserCode(order.getPlanUserCode());
        response.setTargetPlanCode(targetPlanCode);
        response.setTargetPlanGroup(order.getPlanGroup());
        response.setTargetFee(corePaymentPlanService.findPlanByCode(targetPlanCode).getFee());
        response.setTargetPlanDay(order.getPlanDay());
        response.setTargetEnvCount(
                corePaymentPlanService.findPlanByCode(targetPlanCode).getEnvCount());
        response.setFeeDelta(order.getFee());
        response.setNewActiveDate(order.getActiveDate());
        response.setNewEndedDate(order.getEndedDate());
        response.setPaymentCode(payment.getPaymentCode());
        response.setPaymentUrl(paymentUrl);
        response.setStatus(payment.getStatus());
        response.setSellerUsername(order.getUsername());
        return response;
    }

    /**
     * Conversion of a plan that already costs as much as the target, or more:
     * nothing is owed, so no order and no payment are created and the plan is
     * rebuilt on the spot. The surplus is forfeited, which the seller accepted
     * before this is reached.
     */
    @Transactional
    public PlanUserModel convertWithoutPayment(String sourcePlanUserCode, String targetPlanCode,
            String adminUsername) {
        PlanUserModel rebuilt = corePaymentPlanService.convertWithoutPayment(sourcePlanUserCode,
                targetPlanCode);
        notifyPlanConverted(rebuilt.getUsername(), rebuilt, adminUsername);
        return rebuilt;
    }

    /**
     * Payment page URL for a payment code: a fresh secured link holding the
     * code, valid for {@code payment.link.expiry}. Shared by the plan purchase,
     * the extension order and the regeneration, so all three hand out the same
     * kind of URL.
     */
    private String buildPaymentUrl(String paymentCode) {
        LinkModel linkModel = linkService.generateSecureUrl(
                "payment",
                Map.of(LinkConstant.PARAM_PAYMENT_CODE, paymentCode),
                paymentLinkExpiryMillis);
        return LinkBuilder.buildPaymentLink(linkModel.getToken());
    }

    /**
     * Re-issue the payment URL of the selected assignments. The payment keeps
     * its code and status, only the token is rebuilt, so a seller who lost the
     * link or let it expire gets a working one without a new payment record.
     */
    @Transactional
    public List<PaymentModel> regeneratePaymentLinks(List<String> planUserCodes) {
        Map<String, PaymentModel> latest = latestPaymentsByAssignment(planUserCodes);
        List<PaymentModel> regenerated = new ArrayList<>();
        for (String planUserCode : planUserCodes) {
            PaymentModel payment = requirePayment(latest, planUserCode);
            String paymentUrl = buildPaymentUrl(payment.getPaymentCode());
            corePaymentService.updatePaymentUrl(payment.getPaymentCode(), paymentUrl);
            payment.setPaymentUrl(paymentUrl);
            regenerated.add(payment);
        }
        log.info("Regenerated {} payment link(s)", regenerated.size());
        return regenerated;
    }

    private PaymentModel requirePayment(Map<String, PaymentModel> latest, String planUserCode) {
        PaymentModel payment = latest.get(planUserCode);
        if (payment == null) {
            throw new ApplicationException(
                    "No payment found for plan [" + planUserCode + "]. Link a plan to the seller first.", 400);
        }
        return payment;
    }
}
