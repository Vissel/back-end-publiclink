package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.paymentplan.PaymentDomainService;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.domain.paymentplan.event.PaymentCreatedEvent;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.request.SelectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.PlanCatalogResponse;
import com.qrpublic.apartment.paymentplan.response.PaymentInfoResponse;
import com.qrpublic.apartment.paymentplan.response.SelfServiceStateResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Self-service plan purchase: the seller picks a catalog plan themselves and
 * gets the transfer instructions on the spot, instead of waiting for an admin
 * to send a payment link.
 *
 * <p>
 * The money side is unchanged: the order is an ordinary
 * {@code payment_plan_user} row in CREATE plus its payment, so the admin
 * verifies the bank transfer in the same "Waiting for pay" table and activates
 * it with the same mark-paid action. The only new rule is who receives it -
 * the configured self-service admin owns the receiving accounts and the
 * notification.
 */
@Slf4j
@Service
public class SelfServicePlanService {

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private PaymentDomainService paymentDomainService;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Admin whose transfer accounts receive self-service payments. Left blank
     * the feature refuses to sell, rather than silently take money nobody
     * asked for.
     */
    @Value("${app.payment.self-service-admin:}")
    private String selfServiceAdmin;

    /**
     * Buy the selected plan, or report the order that is already open. Choosing
     * twice never double bills: an in-flight order is handed back with its own
     * payment instructions.
     */
    public Result<SelfServiceStateResponse> selectPlan(String username, SelectPlanRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, SelfServiceStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String sellerUsername) {
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                        Assert.notNull(request, "A plan must be selected.");
                        Assert.hasText(request.getPlanCode(), "A plan must be selected.");
                    }

                    @Override
                    public SelfServiceStateResponse process() {
                        return buyPlan(username, request.getPlanCode().trim());
                    }
                });
    }

    /**
     * Onboarding state of the seller, for the plan screen and its polling.
     */
    public Result<SelfServiceStateResponse> currentState(String username) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, SelfServiceStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String sellerUsername) {
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                    }

                    @Override
                    public SelfServiceStateResponse process() {
                        return buildState(username);
                    }
                });
    }

    private SelfServiceStateResponse buyPlan(String username, String planCode) {
        requireSelfServiceAdmin();
        assertLocalAccount(username);
        assertPlanOnSale(planCode);

        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            log.info("Seller [{}] picked a plan again; returning the open order [{}]", username,
                    open.getPlanUserCode());
            return buildState(assignments, username);
        }
        if (hasUsablePlan(username)) {
            // Nothing to buy: a running plan with quota left already covers them.
            return buildState(assignments, username);
        }

        CreateSellerPlanRequest createRequest = new CreateSellerPlanRequest();
        createRequest.setUsername(username);
        createRequest.setPlanCode(planCode);
        CreateSellerPlanResponse created = paymentPlanService.createSellerPlan(createRequest, selfServiceAdmin);

        // Same hand-off as the admin flow: the assignment and payment are
        // committed, so the QR generator can build the VietQR against them.
        eventPublisher.publishEvent(new PaymentCreatedEvent(
                created.getPaymentCode(),
                created.getFee(),
                username,
                buildPlanLabel(created.getFee(), created.getPlanDay()),
                selfServiceAdmin));

        log.info("Seller [{}] self-served plan [{}] -> payment [{}]", username, planCode,
                created.getPaymentCode());
        return buildState(username);
    }

    private SelfServiceStateResponse buildState(String username) {
        return buildState(paymentPlanService.assignmentsWithPayments(username), username);
    }

    private SelfServiceStateResponse buildState(List<PlanUserModel> assignments, String username) {
        SelfServiceStateResponse response = new SelfServiceStateResponse();
        response.setContactEmail(coreUserService.findUserEntityByUsername(username)
                .map(User::getEmail)
                .orElse(null));

        PlanUserModel active = assignments.stream()
                .filter(assignment -> "ACTIVE".equals(assignment.getPlanState()))
                .findFirst()
                .orElse(null);
        response.setActivePlan(active);

        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            PaymentInfoResponse info = paymentInfoOf(open);
            response.setPendingPayment(info);
            response.setState("WAITING_CONFIRM".equals(open.getPaymentStatus())
                    ? SelfServiceStateResponse.STATE_WAITING_CONFIRM
                    : SelfServiceStateResponse.STATE_WAITING_TRANSFER);
            return response;
        }
        if (active != null && hasUsablePlan(username)) {
            response.setState(SelfServiceStateResponse.STATE_ACTIVE);
            return response;
        }
        response.setState(SelfServiceStateResponse.STATE_PICK_PLAN);
        return response;
    }

    /**
     * Newest assignment still in CREATE with a payment that has not been
     * settled: that is the order the seller has to pay.
     */
    private PlanUserModel firstOpenOrder(List<PlanUserModel> assignments) {
        return assignments.stream()
                .filter(assignment -> "CREATE".equals(assignment.getPlanState()))
                .filter(assignment -> assignment.getPaymentCode() != null)
                .filter(assignment -> "CREATE".equals(assignment.getPaymentStatus())
                        || "WAITING_CONFIRM".equals(assignment.getPaymentStatus()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Transfer instructions of one open order. A link that aged out is
     * re-issued on the spot: the payment keeps its code and status, only the
     * token is fresh, so a seller who left the screen open for days can still
     * pay without asking an admin for a new link.
     */
    private PaymentInfoResponse paymentInfoOf(PlanUserModel assignment) {
        String token = extractToken(assignment.getPaymentUrl());
        if (token == null) {
            log.warn("Assignment [{}] has payment [{}] without a usable payment URL",
                    assignment.getPlanUserCode(), assignment.getPaymentCode());
            return null;
        }
        try {
            PaymentInfoResponse info = paymentDomainService.paymentInfo(token);
            info.setPaymentToken(token);
            return info;
        } catch (ApplicationException expiredOrInvalid) {
            log.info("Payment link of assignment [{}] is no longer accepted, re-issuing it",
                    assignment.getPlanUserCode());
        }

        List<PaymentModel> regenerated = paymentPlanService
                .regeneratePaymentLinks(List.of(assignment.getPlanUserCode()));
        if (regenerated.isEmpty()) {
            return null;
        }
        String freshToken = extractToken(regenerated.get(0).getPaymentUrl());
        if (freshToken == null) {
            return null;
        }
        PaymentInfoResponse info = paymentDomainService.paymentInfo(freshToken);
        info.setPaymentToken(freshToken);
        return info;
    }

    private void requireSelfServiceAdmin() {
        if (selfServiceAdmin == null || selfServiceAdmin.isBlank()) {
            log.error("Self-service plan sales are unreachable: app.payment.self-service-admin is not set");
            throw new ApplicationException(
                    "Self-service plan purchase is not configured yet. Please contact admin.", 500);
        }
    }

    /**
     * A plan is sold to the account that pays for it, and that account has to
     * exist locally for the assignment row to have something to point at. An
     * account without a local row (an admin, for instance) gets a readable
     * refusal instead of a foreign key failure deep in the create flow.
     */
    private void assertLocalAccount(String username) {
        if (coreUserService.findUserEntityByUsername(username).isEmpty()) {
            throw new ApplicationException(
                    "This account is not ready to buy a plan yet. Please contact admin.", 400);
        }
    }

    private void assertPlanOnSale(String planCode) {
        boolean onSale = paymentPlanService.catalog().stream()
                .anyMatch(plan -> planCode.equals(plan.getPlanCode()));
        if (!onSale) {
            throw new ApplicationException("This plan is no longer on sale. Please pick another one.", 400);
        }
    }

    private boolean hasUsablePlan(String username) {
        return paymentPlanService.remainingQuota(username) > 0;
    }

    /**
     * Plan part of the bank transfer content, formatted exactly like the admin
     * flow so the QR content stays whole-VND and ASCII-safe.
     */
    private static String buildPlanLabel(BigDecimal fee, int planDay) {
        String amount = fee == null ? "0" : fee.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return amount + " VND " + planDay + "Days";
    }

    private String extractToken(String paymentUrl) {
        if (paymentUrl == null || paymentUrl.isBlank()) {
            return null;
        }
        String token = UriComponentsBuilder.fromUriString(paymentUrl)
                .build()
                .getQueryParams()
                .getFirst("token");
        return token == null || token.isBlank() ? null : token;
    }

    /** Catalog view for the public plan screen. */
    public Result<PlanCatalogResponse> publicCatalog() {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, PlanCatalogResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // nothing to validate: the catalog is open to anyone
            }

            @Override
            public PlanCatalogResponse process() {
                List<PaymentPlanModel> plans = paymentPlanService.catalog();
                PlanCatalogResponse response = new PlanCatalogResponse();
                response.setItems(plans.stream().map(plan -> {
                    PlanCatalogResponse.PlanItem item = new PlanCatalogResponse.PlanItem();
                    item.setPlanCode(plan.getPlanCode());
                    item.setFee(plan.getFee());
                    item.setPlanDay(plan.getPlanDay());
                    item.setEnvCount(plan.getEnvCount());
                    item.setDescription(plan.getDescription());
                    return item;
                }).toList());
                return response;
            }
        });
    }
}
