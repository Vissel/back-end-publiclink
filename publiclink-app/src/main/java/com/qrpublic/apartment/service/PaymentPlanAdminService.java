package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.paymentplan.request.ApprovePlanRequest;
import com.qrpublic.apartment.paymentplan.request.CheckPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ListPlanUserRequest;
import com.qrpublic.apartment.paymentplan.request.MarkPaidRequest;
import com.qrpublic.apartment.paymentplan.request.RejectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CheckPlanResponse;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ListPlanUserResponse;
import com.qrpublic.apartment.paymentplan.response.PlanCatalogResponse;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Admin-facing payment plan operations. This is the only layer that runs
 * PublicLinkServiceTemplate; all business rules live in the domain.
 */
@Slf4j
@Service
public class PaymentPlanAdminService {

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

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
                        Assert.hasText(adminUsername, "Admin identity is required.");
                        boolean hasPlanCode = req.getPlanCode() != null && !req.getPlanCode().isBlank();
                        boolean hasNewPlan = req.getNewPlan() != null && req.getNewPlan().getFee() != null;
                        Assert.isTrue(hasPlanCode || hasNewPlan, "Either an existing plan or a new plan is required.");
                    }

                    @Override
                    public CreateSellerPlanResponse process() {
                        return paymentPlanService.createSellerPlan(request, adminUsername);
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
                com.qrpublic.apartment.core.model.PlanGateModel gate = paymentPlanService
                        .evaluate(request.getUsername().trim());
                CheckPlanResponse response = new CheckPlanResponse();
                response.setAllowed(gate.isAllowed());
                response.setRemaining(gate.getRemaining());
                response.setReason(gate.getReason());
                response.setActiveDate(gate.getActiveDate());
                response.setEndedDate(gate.getEndedDate());
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
                return paymentPlanService.approve(request.getPlanUserCodes(), request.getActiveDate());
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

    public Result<Void> markPaid(MarkPaidRequest request, String adminUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<MarkPaidRequest, Void>() {
            @Override
            public MarkPaidRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(MarkPaidRequest req) {
                Assert.hasText(req.getPaymentCode(), "Payment code is required.");
                Assert.hasText(adminUsername, "Admin identity is required.");
            }

            @Override
            public Void process() {
                paymentPlanService.markPaid(request.getPaymentCode(), request.getNote(), adminUsername);
                return null;
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
        item.setActiveDate(model.getActiveDate());
        item.setEndedDate(model.getEndedDate());
        item.setCreatedAt(model.getCreatedAt());
        item.setPaymentCode(model.getPaymentCode());
        item.setPaymentStatus(model.getPaymentStatus());
        return item;
    }
}
