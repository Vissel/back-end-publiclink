package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.paymentplan.PaymentDomainService;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.domain.paymentplan.event.PaymentCreatedEvent;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.model.PlanGroup;
import com.qrpublic.apartment.model.UserType;
import com.qrpublic.apartment.paymentplan.request.ChangePlanPurposeRequest;
import com.qrpublic.apartment.paymentplan.request.ConvertPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ExtendPlanRequest;
import com.qrpublic.apartment.paymentplan.request.PlanWindowRequest;
import com.qrpublic.apartment.paymentplan.request.SelectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ConvertPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ExtendPlanResponse;
import com.qrpublic.apartment.paymentplan.response.MorePlanStateResponse;
import com.qrpublic.apartment.paymentplan.response.PlanCatalogResponse;
import com.qrpublic.apartment.paymentplan.response.PaymentInfoResponse;
import com.qrpublic.apartment.paymentplan.response.PlanWindowResponse;
import com.qrpublic.apartment.paymentplan.response.SelfServiceStateResponse;
import com.qrpublic.apartment.util.DateUtils;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    /**
     * Upper bound of one self-served extension, the same sanity limit the admin
     * flow uses.
     */
    private static final int MAX_EXTEND_DAYS = 3650;

    /**
     * Bank transfer content of an extension order, worded exactly like the
     * admin flow so the QR content stays recognisable at the bank either way.
     */
    private static final String EXTEND_CONTENT = "mở rộng môi trường thêm %d ngày";

    /**
     * Wire format of the chosen plan window: whole minutes, no zone offset,
     * exactly what the plan screen's date picker reads and writes.
     */
    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private PaymentDomainService paymentDomainService;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private CoreTimezoneService coreTimezoneService;

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
                        return buyPlan(username, request.getPlanCode().trim(), request.getStartAt());
                    }
                });
    }

    /**
     * What window a plan would run for, so the seller can move the start and see
     * the end follow. Nothing is reserved and nothing is written: the same call
     * is made on opening the popup and on every change of the start, and the
     * order only records the answer once the seller confirms it.
     */
    public Result<PlanWindowResponse> planWindow(String username, PlanWindowRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, PlanWindowResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                        Assert.notNull(request, "A plan must be selected.");
                        Assert.hasText(request.getPlanCode(), "A plan must be selected.");
                    }

                    @Override
                    public PlanWindowResponse process() {
                        return calculateWindow(username, request);
                    }
                });
    }

    private PlanWindowResponse calculateWindow(String username, PlanWindowRequest request) {
        // Same gate as buying: an admin or an unknown account gets the readable
        // refusal here rather than a popup that leads nowhere.
        purchasingAccountOrThrow(username);
        PaymentPlanModel plan = findOnSalePlan(request.getPlanCode().trim());
        Timestamp start = resolveWindowStart(request.getStartAt());
        // The same length rule as activation, so the end shown here is the end
        // the plan really gets once the money is confirmed.
        Timestamp end = Timestamp.valueOf(start.toLocalDateTime().plusDays(plan.getPlanDay()));

        PlanWindowResponse response = new PlanWindowResponse();
        response.setStartAt(WINDOW_FORMAT.format(start.toLocalDateTime()));
        response.setEndAt(WINDOW_FORMAT.format(end.toLocalDateTime()));
        response.setPlanDay(plan.getPlanDay());
        return response;
    }

    /**
     * The chosen start, or now when the seller has not touched it yet. Truncated
     * to the minute because that is the picker's own granularity: the minute the
     * seller is looking at must not read as already gone.
     */
    private Timestamp resolveWindowStart(String startAt) {
        Timestamp now = Timestamp.valueOf(nowToTheMinute());
        if (startAt == null || startAt.isBlank()) {
            return now;
        }
        LocalDateTime requested = parseWindowStart(startAt.trim());
        if (requested.isBefore(now.toLocalDateTime())) {
            throw new ApplicationException(
                    "The start cannot be earlier than now. Please pick a later date.", 400);
        }
        return Timestamp.valueOf(requested);
    }

    private LocalDateTime nowToTheMinute() {
        return new Timestamp(coreTimezoneService.getNow().getTime()).toLocalDateTime()
                .withSecond(0)
                .withNano(0);
    }

    private LocalDateTime parseWindowStart(String value) {
        // The picker writes yyyy-MM-ddTHH:mm; tolerating the space separator and
        // the seconds variant costs nothing and keeps curl calls from failing.
        String normalized = value.replace(' ', 'T');
        try {
            if (normalized.length() <= 10) {
                return LocalDate.parse(normalized).atStartOfDay();
            }
            return normalized.length() <= 16
                    ? LocalDateTime.parse(normalized, WINDOW_FORMAT)
                    : LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new ApplicationException(
                    "The start is not a valid date and time. Please pick it again.", 400);
        }
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

    /**
     * "More plan" state: what the account already pays for and the one order it
     * still has to transfer. Read on open and polled while an order is open.
     */
    public Result<MorePlanStateResponse> moreState(String username) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, MorePlanStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                    }

                    @Override
                    public MorePlanStateResponse process() {
                        // Reading is refused for accounts that could never buy
                        // from this screen either, so nobody gets a form that
                        // is guaranteed to reject them.
                        purchasingAccountOrThrow(username);
                        return buildMoreState(username);
                    }
                });
    }

    /**
     * Buy a further plan on top of the one already running. This is what the
     * onboarding screen is not allowed to do: there, a usable plan means
     * nothing is missing; here, buying more is the whole point. The quota of
     * every ACTIVE assignment is summed and coverage takes the latest end
     * date, so a second plan genuinely adds links and days.
     */
    public Result<MorePlanStateResponse> buyMore(String username, SelectPlanRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, MorePlanStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                        Assert.notNull(request, "A plan must be selected.");
                        Assert.hasText(request.getPlanCode(), "A plan must be selected.");
                    }

                    @Override
                    public MorePlanStateResponse process() {
                        return buyAdditionalPlan(username, request.getPlanCode().trim(), request.getStartAt());
                    }
                });
    }

    /**
     * Buy extra days on a plan the caller owns, self-served: the same extension
     * order the admin opens from the setup page, with the ownership check the
     * admin path does not need.
     */
    public Result<MorePlanStateResponse> extendMore(String username, ExtendPlanRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, MorePlanStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                        Assert.notNull(request, "A plan must be selected.");
                        Assert.hasText(request.getPlanUserCode(), "A plan must be selected.");
                    }

                    @Override
                    public MorePlanStateResponse process() {
                        return extendOwnPlan(username, request.getPlanUserCode().trim(), request.getDays());
                    }
                });
    }

    private SelfServiceStateResponse buyPlan(String username, String planCode, String startAt) {
        requireSelfServiceAdmin();
        assertLocalAccount(username);
        PaymentPlanModel plan = findOnSalePlan(planCode);

        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            log.info("Seller [{}] picked a plan again; returning the open order [{}]", username,
                    open.getPlanUserCode());
            return buildState(assignments, username);
        }
        if (hasUsablePlan(username, purposeOf(username))) {
            // Nothing to buy: a running plan of the group this account registered
            // for already covers it.
            return buildState(assignments, username);
        }
        assertFreePlanStillAvailable(plan, username);

        CreateSellerPlanRequest createRequest = new CreateSellerPlanRequest();
        createRequest.setUsername(username);
        createRequest.setPlanCode(planCode);
        createRequest.setStartAt(startAt);
        CreateSellerPlanResponse created = paymentPlanService.createSellerPlan(createRequest, selfServiceAdmin);

        // Same hand-off as the admin flow: the assignment and payment are
        // committed, so the QR generator can build the VietQR against them. A
        // free plan is already confirmed by createSellerPlan, so there is no
        // transfer to generate a QR for.
        if (!isFree(created.getFee())) {
            eventPublisher.publishEvent(new PaymentCreatedEvent(
                    created.getPaymentCode(),
                    created.getFee(),
                    username,
                    buildPlanLabel(created.getFee(), created.getPlanDay()),
                    selfServiceAdmin));
        }

        log.info("Seller [{}] self-served plan [{}] -> payment [{}] status [{}]", username, planCode,
                created.getPaymentCode(), created.getStatus());
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
        String storedPurpose = coreUserService.findPlanPurpose(username);
        response.setPlanPurpose(PlanGroup.fromPurpose(storedPurpose).name());
        // The register form no longer asks; the choice lives here, so the plan
        // screen has to know whether it still owes the account an answer. A
        // blank store is an account that never chose - legacy ones included -
        // and only those see the choice cards before the catalog.
        response.setPurposeChosen(storedPurpose != null && !storedPurpose.isBlank());
        // Tells the screen to grey the free card out rather than let the seller
        // pick it and get refused only after clicking.
        response.setFreePlanUsed(paymentPlanService.freePlanUsed(username));

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
        if (active != null && hasUsablePlan(username, PlanGroup.fromPurpose(storedPurpose).name())) {
            // Only a plan of the group this account chose (or was registered
            // with) counts as covering it, so a booking seller holding a
            // product plan is still offered the booking catalog instead of
            // being sent away satisfied.
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

    /**
     * Only accounts that can own a plan may buy one. An admin has no row in
     * publiclink-db.user at all, so the missing row already refuses it; the
     * type check refuses anything that is neither a seller nor a buyer, so a
     * future admin-like role cannot slip through the front door.
     */
    private User purchasingAccountOrThrow(String username) {
        User local = coreUserService.findUserEntityByUsername(username)
                .orElseThrow(() -> new ApplicationException(
                        "This account is not ready to buy a plan yet. Please contact admin.", 400));
        String type = local.getType();
        if (!UserType.SELLER.name().equalsIgnoreCase(type) && !UserType.BUYER.name().equalsIgnoreCase(type)) {
            throw new ApplicationException("This account cannot buy plans. Please contact admin.", 403);
        }
        return local;
    }

    private MorePlanStateResponse buyAdditionalPlan(String username, String planCode, String startAt) {
        requireSelfServiceAdmin();
        purchasingAccountOrThrow(username);
        PaymentPlanModel plan = findOnSalePlan(planCode);

        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            // Same rule as the onboarding screen: an unpaid order is handed
            // back rather than a second one being opened against the account.
            log.info("Account [{}] asked for another plan while order [{}] is still open", username,
                    open.getPlanUserCode());
            return buildMoreState(assignments, username);
        }
        // Buying more is not a second chance at the free trial either.
        assertFreePlanStillAvailable(plan, username);

        CreateSellerPlanRequest createRequest = new CreateSellerPlanRequest();
        createRequest.setUsername(username);
        createRequest.setPlanCode(planCode);
        createRequest.setStartAt(startAt);
        CreateSellerPlanResponse created = paymentPlanService.createSellerPlan(createRequest, selfServiceAdmin);

        if (!isFree(created.getFee())) {
            eventPublisher.publishEvent(new PaymentCreatedEvent(
                    created.getPaymentCode(),
                    created.getFee(),
                    username,
                    buildPlanLabel(created.getFee(), created.getPlanDay()),
                    selfServiceAdmin));
        }

        log.info("Account [{}] bought an extra plan [{}] -> payment [{}] status [{}]", username, planCode,
                created.getPaymentCode(), created.getStatus());
        return buildMoreState(username);
    }

    private MorePlanStateResponse extendOwnPlan(String username, String planUserCode, int days) {
        requireSelfServiceAdmin();
        purchasingAccountOrThrow(username);
        Assert.isTrue(days > 0, "At least one day is required.");
        Assert.isTrue(days <= MAX_EXTEND_DAYS,
                "An extension cannot be longer than " + MAX_EXTEND_DAYS + " days.");

        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        // Ownership is settled by the lookup itself: the code is searched only
        // among the caller's own assignments, so another account's plan simply
        // is not there.
        PlanUserModel target = assignments.stream()
                .filter(assignment -> planUserCode.equals(assignment.getPlanUserCode()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        "This plan does not belong to your account. Please contact admin.", 403));
        if (!"ACTIVE".equals(target.getPlanState()) || target.getExtendDays() != null) {
            // An extension order cannot be extended again, and a plan the admin
            // deactivated is not the caller's to revive by paying for it.
            throw new ApplicationException(
                    "Only a running plan can be extended. Please buy a new plan instead.", 400);
        }

        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            log.info("Account [{}] asked to extend a plan while order [{}] is still open", username,
                    open.getPlanUserCode());
            return buildMoreState(assignments, username);
        }

        ExtendPlanResponse created = paymentPlanService.createExtension(planUserCode, days, selfServiceAdmin);

        eventPublisher.publishEvent(new PaymentCreatedEvent(
                created.getPaymentCode(),
                created.getFee(),
                username,
                String.format(EXTEND_CONTENT, created.getDays()),
                selfServiceAdmin));

        log.info("Account [{}] opened extension of plan [{}] by {} day(s) -> payment [{}]", username,
                planUserCode, days, created.getPaymentCode());
        return buildMoreState(username);
    }

    private MorePlanStateResponse buildMoreState(String username) {
        return buildMoreState(paymentPlanService.assignmentsWithPayments(username), username);
    }

    /**
     * Assemble the "More plan" screen from assignments already loaded, so a
     * refusal to open a second order still answers with the open order's own
     * transfer details instead of querying the account twice.
     */
    private MorePlanStateResponse buildMoreState(List<PlanUserModel> assignments, String username) {
        MorePlanStateResponse response = new MorePlanStateResponse();
        response.setContactEmail(coreUserService.findUserEntityByUsername(username)
                .map(User::getEmail)
                .orElse(null));
        response.setPlanPurpose(purposeOf(username));
        response.setFreePlanUsed(paymentPlanService.freePlanUsed(username));
        response.setRemainingQuota(paymentPlanService.remainingQuota(username));
        java.util.Date coverageEnd = paymentPlanService.planCoverageEnd(username);
        response.setCoverageEndDate(coverageEnd == null ? null : DateUtils.dateToString(coverageEnd));
        response.setActivePlans(assignments.stream()
                .filter(assignment -> "ACTIVE".equals(assignment.getPlanState()))
                .toList());

        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            response.setPendingPayment(paymentInfoOf(open));
            response.setState("WAITING_CONFIRM".equals(open.getPaymentStatus())
                    ? MorePlanStateResponse.STATE_WAITING_CONFIRM
                    : MorePlanStateResponse.STATE_WAITING_TRANSFER);
            return response;
        }
        response.setState(MorePlanStateResponse.STATE_READY);
        return response;
    }

    /** A catalog entry priced at zero, which is the one-time free plan. */
    private static boolean isFree(BigDecimal fee) {
        return fee != null && fee.signum() == 0;
    }

    private static boolean isFree(PaymentPlanModel plan) {
        return isFree(plan.getFee());
    }

    /**
     * The free plan is a one-time offer per account, and taking it once is
     * permanent history: an expired or even an admin-rejected free grant still
     * counts, so a second self-service pick is refused rather than quietly
     * extending the trial. Only the admin flow is exempt, because handing out
     * free plans repeatedly is an admin decision to make deliberately.
     */
    private void assertFreePlanStillAvailable(PaymentPlanModel plan, String username) {
        if (isFree(plan) && paymentPlanService.freePlanUsed(username)) {
            throw new ApplicationException(
                    "The free plan can only be taken once per account. Please choose a paid plan, "
                            + "or contact admin.",
                    409);
        }
    }

    /** Usable links within one purpose group. */
    private boolean hasUsablePlan(String username, String planGroup) {
        return paymentPlanService.remainingQuota(username, planGroup) > 0;
    }

    /**
     * The group this account registered to buy from. Blank, which is every
     * account predating the split and every account an admin created, reads as
     * PRODUCT so nothing changes for them.
     */
    private String purposeOf(String username) {
        return PlanGroup.fromPurpose(coreUserService.findPlanPurpose(username)).name();
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
        return publicCatalog(null);
    }

    /**
     * Catalog view for the plan screens. A purpose group narrows the list to the
     * kind of link that account registered to buy; blank returns every ACTIVE
     * plan so the register screen can still preview both price lists.
     */
    public Result<PlanCatalogResponse> publicCatalog(String group) {
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
                boolean grouped = group != null && !group.isBlank();
                List<PaymentPlanModel> plans = grouped
                        ? paymentPlanService.catalog(PlanGroup.fromPurpose(group).name())
                        : paymentPlanService.catalog();
                PlanCatalogResponse response = new PlanCatalogResponse();
                response.setItems(plans.stream().map(plan -> {
                    PlanCatalogResponse.PlanItem item = new PlanCatalogResponse.PlanItem();
                    item.setPlanCode(plan.getPlanCode());
                    item.setFee(plan.getFee());
                    item.setPlanDay(plan.getPlanDay());
                    item.setEnvCount(plan.getEnvCount());
                    item.setDescription(plan.getDescription());
                    item.setPlanGroup(plan.getPlanGroup());
                    return item;
                }).toList());
                return response;
            }
        });
    }

    /**
     * Switch which plan group this account is offered. Deliberately separate
     * from {@code convertPlan}: a conversion stays inside one group, so this is
     * the only way out for someone who answered the registration question wrong.
     */
    public Result<SelfServiceStateResponse> changePurpose(String username, ChangePlanPurposeRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, SelfServiceStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                        Assert.notNull(request, "A purpose must be given.");
                    }

                    @Override
                    public SelfServiceStateResponse process() {
                        return switchPurpose(username, request.getPlanPurpose());
                    }
                });
    }

    private SelfServiceStateResponse switchPurpose(String username, String purpose) {
        User local = purchasingAccountOrThrow(username);
        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        if (firstOpenOrder(assignments) != null) {
            // The unpaid order belongs to the group being left. Switching now
            // would leave the seller staring at a transfer they no longer want.
            throw new ApplicationException(
                    "You still have an order waiting to be paid. Pay or let it expire first, then switch.",
                    400);
        }
        String resolved = PlanGroup.fromPurpose(purpose).name();
        if (!resolved.equals(local.getPlanPurpose())) {
            coreUserService.updatePlanPurpose(local, resolved);
            log.info("Account [{}] switched its plan purpose to {}", username, resolved);
        }
        return buildState(assignments, username);
    }

    /**
     * Convert a running plan onto a dearer one of the same group, charging only
     * the difference. When the difference is zero or negative nothing is
     * charged at all: the plan is converted on the spot and the surplus lost,
     * which the seller confirmed before asking.
     */
    public Result<MorePlanStateResponse> convertPlan(String username, ConvertPlanRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<String, MorePlanStateResponse>() {
                    @Override
                    public String getRequest() {
                        return username;
                    }

                    @Override
                    public void preProcess(String caller) {
                        Assert.hasText(caller, "Account identity is required.");
                        Assert.notNull(request, "A plan must be selected.");
                        Assert.hasText(request.getPlanUserCode(), "A plan must be selected.");
                        Assert.hasText(request.getTargetPlanCode(), "The plan to switch to must be selected.");
                    }

                    @Override
                    public MorePlanStateResponse process() {
                        return convertOwnPlan(username, request.getPlanUserCode().trim(),
                                request.getTargetPlanCode().trim());
                    }
                });
    }

    private MorePlanStateResponse convertOwnPlan(String username, String planUserCode, String targetPlanCode) {
        requireSelfServiceAdmin();
        purchasingAccountOrThrow(username);

        List<PlanUserModel> assignments = paymentPlanService.assignmentsWithPayments(username);
        // Ownership is settled by the lookup itself: the code is searched only
        // among the caller's own assignments, so another account's plan simply
        // is not there.
        PlanUserModel source = assignments.stream()
                .filter(assignment -> planUserCode.equals(assignment.getPlanUserCode()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        "This plan does not belong to your account. Please contact admin.", 403));
        if (!"ACTIVE".equals(source.getPlanState()) || !isPlainPlan(source)) {
            throw new ApplicationException(
                    "Only a running plan can be converted. Please buy a new plan instead.", 400);
        }

        PaymentPlanModel target = findOnSalePlan(targetPlanCode);
        if (!groupOf(source).equals(groupOf(target))) {
            throw new ApplicationException(
                    "A " + groupOf(source) + " plan cannot become a " + groupOf(target)
                            + " plan. Buy a " + groupOf(target) + " plan instead.",
                    400);
        }
        if (isFree(target)) {
            // Converting owes nothing when the target is free, so without this a
            // paid plan could be walked down onto the one-time trial and the
            // free allowance would be spent twice over.
            throw new ApplicationException(
                    "A conversion cannot move onto the free plan. Please pick a paid plan.", 400);
        }

        BigDecimal delta = feeOf(target).subtract(feeOf(source));
        if (delta.signum() <= 0) {
            // Nothing to transfer: convert straight away and say so, so the
            // screen can confirm rather than show an empty payment panel.
            paymentPlanService.convertWithoutPayment(planUserCode, targetPlanCode, selfServiceAdmin);
            MorePlanStateResponse state = buildMoreState(username);
            state.setConversionOutcome(outcome(
                    MorePlanStateResponse.ConversionOutcome.NO_PAYMENT, delta, planUserCode, null, target));
            return state;
        }

        PlanUserModel open = firstOpenOrder(assignments);
        if (open != null) {
            log.info("Account [{}] asked to convert a plan while order [{}] is still open", username,
                    open.getPlanUserCode());
            return buildMoreState(assignments, username);
        }

        ConvertPlanResponse created = paymentPlanService.createConversion(planUserCode, targetPlanCode,
                selfServiceAdmin);
        eventPublisher.publishEvent(new PaymentCreatedEvent(
                created.getPaymentCode(),
                created.getFeeDelta(),
                username,
                buildConvertLabel(created.getFeeDelta(), created.getTargetPlanDay()),
                selfServiceAdmin));

        log.info("Account [{}] opened conversion of plan [{}] to [{}] for {}", username, planUserCode,
                targetPlanCode, created.getFeeDelta().toPlainString());
        MorePlanStateResponse state = buildMoreState(username);
        state.setConversionOutcome(outcome(MorePlanStateResponse.ConversionOutcome.ORDER_CREATED, delta,
                planUserCode, created.getConversionPlanUserCode(), target));
        return state;
    }

    /**
     * The outcome echoed back to the screen, so it can render the difference and
     * the new window without re-deriving either from the plan list.
     */
    private MorePlanStateResponse.ConversionOutcome outcome(String result, BigDecimal delta, String sourceCode,
            String orderCode, PaymentPlanModel target) {
        MorePlanStateResponse.ConversionOutcome outcome = new MorePlanStateResponse.ConversionOutcome();
        outcome.setResult(result);
        outcome.setFeeDelta(delta);
        outcome.setSourcePlanUserCode(sourceCode);
        outcome.setConversionPlanUserCode(orderCode);
        outcome.setTargetPlanCode(target.getPlanCode());
        outcome.setTargetFee(target.getFee());
        outcome.setTargetPlanDay(target.getPlanDay());
        outcome.setTargetEnvCount(target.getEnvCount());
        return outcome;
    }

    /** An ACTIVE assignment that is neither an extension nor a conversion order. */
    private boolean isPlainPlan(PlanUserModel assignment) {
        return assignment.getExtendDays() == null && assignment.getConvertsPlanUserCode() == null;
    }

    private PaymentPlanModel findOnSalePlan(String planCode) {
        return paymentPlanService.catalog().stream()
                .filter(plan -> planCode.equals(plan.getPlanCode()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        "This plan is no longer on sale. Please pick another one.", 400));
    }

    private String groupOf(PaymentPlanModel plan) {
        return plan.getPlanGroup() == null || plan.getPlanGroup().isBlank()
                ? PlanGroup.PRODUCT.name()
                : plan.getPlanGroup();
    }

    private String groupOf(PlanUserModel assignment) {
        return assignment.getPlanGroup() == null || assignment.getPlanGroup().isBlank()
                ? PlanGroup.PRODUCT.name()
                : assignment.getPlanGroup();
    }

    private BigDecimal feeOf(PaymentPlanModel plan) {
        return plan.getFee() == null ? BigDecimal.ZERO : plan.getFee();
    }

    private BigDecimal feeOf(PlanUserModel assignment) {
        return assignment.getFee() == null ? BigDecimal.ZERO : assignment.getFee();
    }

    /**
     * Conversion part of the bank transfer content, worded like the extension's
     * so the QR content stays recognisable at the bank either way.
     */
    private static String buildConvertLabel(BigDecimal delta, int planDay) {
        String amount = delta == null ? "0" : delta.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return "nang loi " + amount + " " + planDay + "Days";
    }
}
