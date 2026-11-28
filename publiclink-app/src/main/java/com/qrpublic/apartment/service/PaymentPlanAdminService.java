package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.domain.paymentplan.event.PaymentCreatedEvent;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.paymentplan.request.ApprovePlanRequest;
import com.qrpublic.apartment.paymentplan.request.CheckPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateCatalogPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ExtendPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ListPlanUserRequest;
import com.qrpublic.apartment.paymentplan.request.MarkPaidRequest;
import com.qrpublic.apartment.paymentplan.request.RegenerateLinkRequest;
import com.qrpublic.apartment.paymentplan.request.RejectPlanRequest;
import com.qrpublic.apartment.paymentplan.request.StartPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CheckPlanResponse;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.CreatedPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ExtendPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ListPlanUserResponse;
import com.qrpublic.apartment.paymentplan.response.PlanCatalogResponse;
import com.qrpublic.apartment.paymentplan.response.RegenerateLinkResponse;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Admin-facing payment plan operations. This is the only layer that runs
 * PublicLinkServiceTemplate; all business rules live in the domain.
 */
@Slf4j
@Service
public class PaymentPlanAdminService {

    /**
     * Transfer content of an extension order, in Vietnamese because that is
     * what the seller copies into the bank transfer. The QR generator drops the
     * diacritics, as a Napas transfer content has to stay ASCII.
     */
    private static final String EXTEND_CONTENT = "mở rộng môi trường thêm %d ngày";

    /** sanity bound for a single extension order */
    private static final int MAX_EXTEND_DAYS = 3650;

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Create a catalog plan. Deliberately takes no seller: the plan stands on
     * its own and is linked afterwards by createSellerPlan.
     */
    public Result<CreatedPlanResponse> createCatalogPlan(CreateCatalogPlanRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<CreateCatalogPlanRequest, CreatedPlanResponse>() {
                    @Override
                    public CreateCatalogPlanRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(CreateCatalogPlanRequest req) {
                        Assert.notNull(req.getFee(), "Plan fee is required.");
                        // Zero is a free plan and legitimate; only a negative price
                        // is meaningless. Days and links still have to exist.
                        Assert.isTrue(req.getFee().signum() >= 0,
                                "Plan fee must not be negative. Use 0 for a free plan.");
                        Assert.isTrue(req.getPlanDay() > 0, "Plan days must be greater than zero.");
                        Assert.isTrue(req.getEnvCount() > 0, "Environment count must be greater than zero.");
                        assertKnownPlanGroup(req.getPlanGroup());
                    }

                    @Override
                    public CreatedPlanResponse process() {
                        return paymentPlanService.createCatalogPlan(request);
                    }
                });
    }

    public Result<CreateSellerPlanResponse> createSellerPlan(CreateSellerPlanRequest request, String adminUsername) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<CreateSellerPlanRequest, CreateSellerPlanResponse>() {
                    @Override
                    public CreateSellerPlanRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(CreateSellerPlanRequest req) {
                        Assert.hasText(req.getUsername(), "Seller username is required.");
                        Assert.hasText(req.getPlanCode(), "A plan must be selected. Create the plan first.");
                        Assert.hasText(adminUsername, "Admin identity is required.");
                    }

                    @Override
                    public CreateSellerPlanResponse process() {
                        CreateSellerPlanResponse response = paymentPlanService.createSellerPlan(request, adminUsername);
                        // The assignment + payment are committed by the domain call above;
                        // hand off to GenerateQRDomain to build and store the VietQR QR.
                        eventPublisher.publishEvent(new PaymentCreatedEvent(
                                response.getPaymentCode(),
                                response.getFee(),
                                request.getUsername(),
                                buildPlanLabel(response.getFee(), response.getPlanDay()),
                                adminUsername));
                        return response;
                    }
                });
    }

    public Result<CheckPlanResponse> checkPlan(CheckPlanRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<CheckPlanRequest, CheckPlanResponse>() {
            @Override
            public CheckPlanRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(CheckPlanRequest req) {
                Assert.hasText(req.getUsername(), "Seller username is required.");
            }

            @Override
            public CheckPlanResponse process() {
                // A product type narrows the gate to the plan group that kind of
                // link has to be paid for by. Without one the answer stays about
                // the account in general, which is what the workspace banner asks.
                String planGroup = request.getProductType() == null || request.getProductType().isBlank()
                        ? null
                        : com.qrpublic.apartment.model.PlanGroup.fromProductType(request.getProductType()).name();
                com.qrpublic.apartment.core.model.PlanGateModel gate = paymentPlanService
                        .evaluate(request.getUsername().trim(), planGroup);
                CheckPlanResponse response = new CheckPlanResponse();
                response.setAllowed(gate.isAllowed());
                response.setRemaining(gate.getRemaining());
                response.setReason(gate.getReason());
                response.setPlanGroup(gate.getPlanGroup());
                response.setActiveDate(gate.getActiveDate());
                response.setEndedDate(gate.getEndedDate());
                response.setCoverageEndedDate(gate.getCoverageEndedDate());
                response.setMessage(gate.isAllowed() ? null : gate.getMessage());
                return response;
            }
        });
    }

    public Result<PlanCatalogResponse> catalog() {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, PlanCatalogResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // no validation needed
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
                    item.setPlanGroup(plan.getPlanGroup());
                    return item;
                }).toList());
                return response;
            }
        });
    }

    /**
     * Setup page tables: state filter (ACTIVE default / CREATE for the
     * approval table / ALL), username search and pagination in one payload.
     */
    public Result<ListPlanUserResponse> listPlanUsers(Pagination<ListPlanUserRequest> pagination) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<Pagination<ListPlanUserRequest>, ListPlanUserResponse>() {
                    @Override
                    public Pagination<ListPlanUserRequest> getRequest() {
                        return pagination;
                    }

                    @Override
                    public void preProcess(Pagination<ListPlanUserRequest> req) {
                        Assert.notNull(req, "Pagination payload is required.");
                        Assert.isTrue(req.getPage() >= 0, "Page must not be negative.");
                        Assert.isTrue(req.getSize() > 0, "Size must be positive.");
                    }

                    @Override
                    public ListPlanUserResponse process() {
                        ListPlanUserRequest filter = firstFilter(pagination);
                        String state = filter == null ? null : filter.getState();
                        String username = filter == null ? null : filter.getUsername();
                        List<PlanUserModel> assignments = paymentPlanService.listAssignments(
                                state, username, pagination.getPage(), pagination.getSize());
                        paymentPlanService.enrichWithPayment(assignments);
                        return buildList(assignments, paymentPlanService.countAssignments(state, username));
                    }
                });
    }

    /**
     * ACTIVE assignments of one seller (admin step-1 select and seller
     * self-view).
     */
    public Result<ListPlanUserResponse> listUserPlans(String username) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, ListPlanUserResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(username, "Seller username is required.");
            }

            @Override
            public ListPlanUserResponse process() {
                List<PlanUserModel> assignments = paymentPlanService.activeAssignments(username.trim());
                paymentPlanService.enrichWithPayment(assignments);
                return buildList(assignments, assignments.size());
            }
        });
    }

    /**
     * Sell extra days on a plan that is already running. The extension becomes
     * its own "waiting for pay" row with its own payment link, so the seller
     * pays it like a new plan; the extended plan only gains the days once that
     * row is marked paid.
     */
    public Result<ExtendPlanResponse> extendPlan(ExtendPlanRequest request, String adminUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<ExtendPlanRequest, ExtendPlanResponse>() {
            @Override
            public ExtendPlanRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ExtendPlanRequest req) {
                Assert.hasText(adminUsername, "Admin username is required.");
                Assert.notNull(req, "Request is required.");
                Assert.hasText(req.getPlanUserCode(), "Plan user code is required.");
                Assert.isTrue(req.getDays() > 0, "At least one day is required.");
                Assert.isTrue(req.getDays() <= MAX_EXTEND_DAYS,
                        "An extension cannot be longer than " + MAX_EXTEND_DAYS + " days.");
            }

            @Override
            public ExtendPlanResponse process() {
                ExtendPlanResponse response = paymentPlanService.createExtension(
                        request.getPlanUserCode().trim(), request.getDays(), adminUsername);
                eventPublisher.publishEvent(new PaymentCreatedEvent(
                        response.getPaymentCode(),
                        response.getFee(),
                        response.getSellerUsername(),
                        String.format(EXTEND_CONTENT, response.getDays()),
                        adminUsername));
                return response;
            }
        });
    }

    public Result<Integer> approve(ApprovePlanRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<ApprovePlanRequest, Integer>() {
            @Override
            public ApprovePlanRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ApprovePlanRequest req) {
                Assert.notEmpty(req.getPlanUserCodes(), "At least one plan is required.");
            }

            @Override
            public Integer process() {
                return paymentPlanService.approve(request.getPlanUserCodes(), request.getStartAt());
            }
        });
    }

    /**
     * Admin correction of a plan's start, available while the order waits for
     * payment and after it is running. Used by the per-row action of the setup
     * page, where the bulk date field would apply one start to every selection.
     */
    public Result<Integer> changeStart(StartPlanRequest request, String adminUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<StartPlanRequest, Integer>() {
            @Override
            public StartPlanRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(StartPlanRequest req) {
                Assert.notEmpty(req.getPlanUserCodes(), "At least one plan is required.");
                Assert.hasText(req.getStartAt(), "A start date and time is required.");
                Assert.hasText(adminUsername, "Admin identity is required.");
            }

            @Override
            public Integer process() {
                return paymentPlanService.changeStart(request.getPlanUserCodes(), request.getStartAt(),
                        adminUsername);
            }
        });
    }

    public Result<Integer> reject(RejectPlanRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<RejectPlanRequest, Integer>() {
            @Override
            public RejectPlanRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(RejectPlanRequest req) {
                Assert.notEmpty(req.getPlanUserCodes(), "At least one plan is required.");
            }

            @Override
            public Integer process() {
                return paymentPlanService.reject(request.getPlanUserCodes());
            }
        });
    }

    /**
     * Admin received the money: payments go to PAID and their CREATE plans
     * start on the chosen activation date, so they jump straight into the
     * active plan list.
     */
    public Result<Integer> markPaid(MarkPaidRequest request, String adminUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<MarkPaidRequest, Integer>() {
            @Override
            public MarkPaidRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(MarkPaidRequest req) {
                Assert.notEmpty(req.getPlanUserCodes(), "At least one payment plan is required.");
                Assert.hasText(adminUsername, "Admin identity is required.");
            }

            @Override
            public Integer process() {
                return paymentPlanService.markPaidAssignments(
                        request.getPlanUserCodes(), request.getNote(), adminUsername,
                        request.getStartAt());
            }
        });
    }

    /**
     * Fresh payment URLs for the selected assignments, so the admin can resend
     * a link a seller lost or that already expired.
     */
    public Result<RegenerateLinkResponse> regeneratePaymentLink(RegenerateLinkRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<RegenerateLinkRequest, RegenerateLinkResponse>() {
                    @Override
                    public RegenerateLinkRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(RegenerateLinkRequest req) {
                        Assert.notEmpty(req.getPlanUserCodes(), "At least one payment plan is required.");
                    }

                    @Override
                    public RegenerateLinkResponse process() {
                        List<PaymentModel> payments = paymentPlanService
                                .regeneratePaymentLinks(request.getPlanUserCodes());
                        RegenerateLinkResponse response = new RegenerateLinkResponse();
                        response.setProcessed(payments.size());
                        response.setItems(payments.stream().map(payment -> {
                            RegenerateLinkResponse.PaymentLinkItem item = new RegenerateLinkResponse.PaymentLinkItem();
                            item.setPlanUserCode(payment.getPlanUserCode());
                            item.setUsername(payment.getUsername());
                            item.setPaymentCode(payment.getPaymentCode());
                            item.setPaymentStatus(payment.getStatus());
                            item.setPaymentUrl(payment.getPaymentUrl());
                            return item;
                        }).toList());
                        return response;
                    }
                });
    }

    /**
     * Quota gate used by the Generate Product Link flow. Called from the sale
     * environment creation path, so it must not swallow the domain exception.
     */
    public void consumeQuota(String username) {
        if (username == null || username.isBlank()) {
            throw new ApplicationException("Seller username is required to check the payment plan.", 400);
        }
        paymentPlanService.consumeQuotaOrThrow(username.trim());
    }

    private ListPlanUserRequest firstFilter(Pagination<ListPlanUserRequest> pagination) {
        if (pagination.getListData() == null || pagination.getListData().isEmpty()) {
            return null;
        }
        return pagination.getListData().get(0);
    }

    private ListPlanUserResponse buildList(List<PlanUserModel> assignments, long total) {
        ListPlanUserResponse response = new ListPlanUserResponse();
        response.setTotal(total);
        response.setItems(assignments.stream().map(this::toItem).toList());
        return response;
    }

    /**
     * A plan group must be one of the two the catalog is split into. Blank is
     * allowed and means PRODUCT, so an admin who does not choose still creates
     * the kind of plan that has always been on offer.
     */
    private void assertKnownPlanGroup(String planGroup) {
        if (planGroup == null || planGroup.isBlank()) {
            return;
        }
        String wanted = planGroup.trim().toUpperCase();
        boolean known = java.util.Arrays.stream(com.qrpublic.apartment.model.PlanGroup.values())
                .anyMatch(group -> group.name().equals(wanted));
        if (!known) {
            throw new ApplicationException(
                    "Unknown plan group [" + planGroup + "]. Use PRODUCT or BOOKING.", 400);
        }
    }

    private ListPlanUserResponse.PlanUserItem toItem(PlanUserModel model) {
        ListPlanUserResponse.PlanUserItem item = new ListPlanUserResponse.PlanUserItem();
        item.setPlanUserCode(model.getPlanUserCode());
        item.setUsername(model.getUsername());
        item.setDisplayName(model.getDisplayName());
        item.setFee(model.getFee());
        item.setPlanDay(model.getPlanDay());
        item.setEnvQuota(model.getEnvQuota());
        item.setUsedCount(model.getUsedCount());
        item.setRemaining(model.getRemaining());
        item.setPlanState(model.getPlanState());
        item.setPlanGroup(model.getPlanGroup());
        item.setActiveDate(model.getActiveDate());
        item.setEndedDate(model.getEndedDate());
        item.setRequestedStartAt(model.getRequestedStartAt());
        item.setExtendDays(model.getExtendDays());
        item.setExtendsPlanUserCode(model.getExtendsPlanUserCode());
        item.setConvertsPlanUserCode(model.getConvertsPlanUserCode());
        item.setConvertFeeDelta(model.getConvertFeeDelta());
        item.setCreatedAt(model.getCreatedAt());
        item.setPaymentCode(model.getPaymentCode());
        item.setPaymentStatus(model.getPaymentStatus());
        item.setPaymentUrl(model.getPaymentUrl());
        return item;
    }

    /**
     * Plan part of the bank transfer content: whole-VND amount, currency and the
     * covered days, e.g. {@code 10000 VND 7Days}. Rounded the same way as the
     * amount encoded in the QR, so the number the seller reads is the number the
     * bank receives. Decimals must stay out: the content is stripped to ASCII
     * letters and digits, so "10000.00" would silently turn into "1000000".
     */
    private static String buildPlanLabel(BigDecimal fee, int planDay) {
        String amount = fee == null ? "0" : fee.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return amount + " VND " + planDay + "Days";
    }
}
