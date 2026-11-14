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
import com.qrpublic.apartment.paymentplan.response.CreatedPlanResponse;
import com.qrpublic.apartment.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                request.getFee(), request.getPlanDay(), envCount, request.getDescription());

        CreatedPlanResponse response = new CreatedPlanResponse();
        response.setPlanCode(plan.getPlanCode());
        response.setFee(plan.getFee());
        response.setPlanDay(plan.getPlanDay());
        response.setEnvCount(plan.getEnvCount());
        response.setDescription(plan.getDescription());
        response.setPlanState(plan.getPlanState());
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

        LinkModel linkModel = linkService.generateSecureUrl(
                "payment",
                Map.of(LinkConstant.PARAM_PAYMENT_CODE, payment.getPaymentCode()),
                paymentLinkExpiryMillis);
        String paymentUrl = LinkBuilder.buildPaymentLink(linkModel.getToken());
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

    @Transactional(readOnly = true)
    public List<PlanUserModel> activeAssignments(String username) {
        return corePaymentPlanService.findActiveAssignments(username);
    }

    /**
     * ACTIVE plan catalog for the select-plan dropdown.
     */
    @Transactional(readOnly = true)
    public List<com.qrpublic.apartment.core.model.PaymentPlanModel> catalog() {
        return corePaymentPlanService.findActivePlans();
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
     * Attach the latest payment code/status to each assignment model (used by
     * the setup tables and mark-paid action).
     */
    @Transactional(readOnly = true)
    public void enrichWithPayment(List<PlanUserModel> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        List<String> codes = assignments.stream().map(PlanUserModel::getPlanUserCode).toList();
        Map<String, PaymentModel> latestByCode = new java.util.HashMap<>();
        // list is ordered createdAt DESC, first seen per code is the latest
        for (PaymentModel payment : corePaymentService.findPaymentsByPlanUserCodes(codes)) {
            latestByCode.putIfAbsent(payment.getPlanUserCode(), payment);
        }
        assignments.forEach(a -> {
            PaymentModel payment = latestByCode.get(a.getPlanUserCode());
            if (payment != null) {
                a.setPaymentCode(payment.getPaymentCode());
                a.setPaymentStatus(payment.getStatus());
                a.setCreatedBy(payment.getCreatedBy());
            }
        });
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
     * Consume one environment slot for the seller. Called inside the sale
     * environment transaction: refuses creation when the plan has not started,
     * has ended, or has no slot left, and returns the plan deadline so the
     * generated link can be clamped to it.
     */
    @Transactional
    public com.qrpublic.apartment.core.model.PlanGateModel consumeQuotaOrThrow(String username) {
        com.qrpublic.apartment.core.model.PlanGateModel gate = corePaymentPlanService.consumeOne(username);
        if (!gate.isAllowed()) {
            log.warn("Refused environment creation for [{}]: {}", username, gate.getReason());
            throw new ApplicationException(gate.getMessage(), 403);
        }
        return gate;
    }

    /**
     * Admin confirms the money arrived: payment -> PAID and a bill is created.
     */
    @Transactional
    public PaymentModel markPaid(String paymentCode, String note, String adminUsername) {
        return corePaymentService.markPaid(paymentCode, note, adminUsername);
    }
}
