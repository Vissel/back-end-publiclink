package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.paymentplan.PaymentDomainService;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.domain.paymentplan.event.PaymentCreatedEvent;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.paymentplan.request.ConvertPlanRequest;
import com.qrpublic.apartment.paymentplan.request.SelectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.MorePlanStateResponse;
import com.qrpublic.apartment.paymentplan.response.SelfServiceStateResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The self-service half of the free-plan rule: one grant per account, ever, and
 * no VietQR for a plan that has nothing to transfer. The real service template
 * is
 * spied rather than mocked so a refusal surfaces as the same Result the
 * controller turns into an HTTP status.
 */
@ExtendWith(MockitoExtension.class)
class SelfServicePlanServiceFreePlanTest {

    private static final String SELLER = "seller-1";
    private static final String FREE_PLAN = "p-free";
    private static final String PAID_PLAN = "p-paid";

    @Mock
    private PaymentPlanService paymentPlanService;
    @Mock
    private PaymentDomainService paymentDomainService;
    @Mock
    private CoreUserService coreUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Spy
    private PublicLinkServiceTemplate publicLinkServiceTemplate = new PublicLinkServiceTemplate();
    @InjectMocks
    private SelfServicePlanService service;

    @BeforeEach
    void wireAdmin() {
        ReflectionTestUtils.setField(service, "selfServiceAdmin", "ops-admin");
    }

    private static PaymentPlanModel plan(String code, long fee) {
        return PaymentPlanModel.builder()
                .planCode(code).fee(BigDecimal.valueOf(fee)).planDay(5).envCount(1)
                .planState("ACTIVE").planGroup("PRODUCT").build();
    }

    private static PlanUserModel runningPlan(String code, long fee) {
        return PlanUserModel.builder()
                .planUserCode(code).username(SELLER).fee(BigDecimal.valueOf(fee)).planDay(5)
                .envQuota(1).planState("ACTIVE").planGroup("PRODUCT").build();
    }

    private User sellerAccount() {
        User user = new User();
        user.setUserName(SELLER);
        user.setType("SELLER");
        user.setEmail("s@example.com");
        return user;
    }

    private void catalogHasFreeAndPaid() {
        when(paymentPlanService.catalog()).thenReturn(List.of(plan(FREE_PLAN, 0), plan(PAID_PLAN, 15000)));
    }

    private SelectPlanRequest select(String planCode) {
        SelectPlanRequest request = new SelectPlanRequest();
        request.setPlanCode(planCode);
        return request;
    }

    private Result<SelfServiceStateResponse> selectPlan(String planCode) {
        return service.selectPlan(SELLER, select(planCode));
    }

    @Test
    void secondFreePlanIsRefusedAndNothingIsCreated() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        catalogHasFreeAndPaid();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of());
        when(coreUserService.findPlanPurpose(SELLER)).thenReturn(null);
        when(paymentPlanService.remainingQuota(SELLER, "PRODUCT")).thenReturn(0);
        when(paymentPlanService.freePlanUsed(SELLER)).thenReturn(true);

        Result<SelfServiceStateResponse> result = selectPlan(FREE_PLAN);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(409);
        assertThat(result.getErrorMessage()).contains("once per account");
        verify(paymentPlanService, never()).createSellerPlan(any(), anyString());
    }

    /**
     * A paid plan must still be sold to an account that used its free trial,
     * otherwise the rule would lock the account out of buying at all. The guard
     * checks the price first and short-circuits, so the allowance is never even
     * looked up for a paid plan.
     */
    @Test
    void paidPlanIsStillSoldToAnAccountThatUsedTheFreeOne() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        catalogHasFreeAndPaid();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of(), List.of());
        when(coreUserService.findPlanPurpose(SELLER)).thenReturn(null);
        when(paymentPlanService.remainingQuota(SELLER, "PRODUCT")).thenReturn(0);
        when(paymentPlanService.createSellerPlan(any(), any())).thenReturn(paidResponse());

        Result<SelfServiceStateResponse> result = selectPlan(PAID_PLAN);

        assertThat(result.isSuccess()).isTrue();
        verify(paymentPlanService).createSellerPlan(any(), any());
    }

    @Test
    void firstFreePlanIsSoldAndNoTransferIsGenerated() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        catalogHasFreeAndPaid();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of(), List.of());
        when(coreUserService.findPlanPurpose(SELLER)).thenReturn(null);
        when(paymentPlanService.remainingQuota(SELLER, "PRODUCT")).thenReturn(0);
        when(paymentPlanService.freePlanUsed(SELLER)).thenReturn(false, true);
        when(paymentPlanService.createSellerPlan(any(), any())).thenReturn(freeResponse());

        Result<SelfServiceStateResponse> result = selectPlan(FREE_PLAN);

        assertThat(result.isSuccess()).isTrue();
        // createSellerPlan already confirmed it, so there is no transfer to make a QR
        // for.
        verify(eventPublisher, never()).publishEvent(any(PaymentCreatedEvent.class));
    }

    @Test
    void paidPlanStillPublishesTheQrHandoff() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        catalogHasFreeAndPaid();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of(), List.of());
        when(coreUserService.findPlanPurpose(SELLER)).thenReturn(null);
        when(paymentPlanService.remainingQuota(SELLER, "PRODUCT")).thenReturn(0);
        when(paymentPlanService.freePlanUsed(SELLER)).thenReturn(false);
        when(paymentPlanService.createSellerPlan(any(), any())).thenReturn(paidResponse());

        selectPlan(PAID_PLAN);

        verify(eventPublisher).publishEvent(any(PaymentCreatedEvent.class));
    }

    @Test
    void buyingMoreIsNotASecondChanceAtTheFreePlan() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        catalogHasFreeAndPaid();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of());
        when(paymentPlanService.freePlanUsed(SELLER)).thenReturn(true);

        Result<MorePlanStateResponse> result = service.buyMore(SELLER, select(FREE_PLAN));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(409);
        verify(paymentPlanService, never()).createSellerPlan(any(), anyString());
    }

    @Test
    void conversionCannotLandOnAFreePlan() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(sellerAccount()));
        when(paymentPlanService.assignmentsWithPayments(SELLER))
                .thenReturn(List.of(runningPlan("pu-paid", 15000)));
        catalogHasFreeAndPaid();

        ConvertPlanRequest request = new ConvertPlanRequest();
        request.setPlanUserCode("pu-paid");
        request.setTargetPlanCode(FREE_PLAN);

        Result<MorePlanStateResponse> result = service.convertPlan(SELLER, request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).contains("free plan");
        verify(paymentPlanService, never()).createConversion(anyString(), anyString(), anyString());
        verify(paymentPlanService, never()).convertWithoutPayment(anyString(), anyString(), anyString());
    }

    private CreateSellerPlanResponse freeResponse() {
        CreateSellerPlanResponse response = new CreateSellerPlanResponse();
        response.setPaymentCode("pay-free");
        response.setFee(BigDecimal.ZERO);
        response.setPlanDay(5);
        response.setEnvCount(1);
        response.setStatus("PAID");
        return response;
    }

    private CreateSellerPlanResponse paidResponse() {
        CreateSellerPlanResponse response = new CreateSellerPlanResponse();
        response.setPaymentCode("pay-paid");
        response.setFee(BigDecimal.valueOf(15000));
        response.setPlanDay(5);
        response.setEnvCount(1);
        response.setStatus("CREATE");
        return response;
    }
}
