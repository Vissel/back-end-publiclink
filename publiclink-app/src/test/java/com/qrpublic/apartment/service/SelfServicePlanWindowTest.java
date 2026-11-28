package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.paymentplan.PaymentDomainService;
import com.qrpublic.apartment.domain.paymentplan.PaymentPlanService;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.request.PlanWindowRequest;
import com.qrpublic.apartment.paymentplan.request.SelectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.paymentplan.response.PlanWindowResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The window endpoint and the start the order carries.
 *
 * The seller sees the end the backend calculated, never one the browser made
 * up,
 * and the start they confirm is what the order records - so the dates on the
 * popup are the dates activation will honour.
 */
@ExtendWith(MockitoExtension.class)
class SelfServicePlanWindowTest {

    private static final String SELLER = "seller-1";
    private static final String PLAN = "p-10";
    /**
     * Fixed clock: 2026-11-28 10:00, already past the second, to prove truncation.
     */
    private static final Timestamp NOW = Timestamp.valueOf(LocalDateTime.of(2026, 11, 28, 10, 0, 37));

    @Mock
    private PaymentPlanService paymentPlanService;
    @Mock
    private PaymentDomainService paymentDomainService;
    @Mock
    private CoreUserService coreUserService;
    @Mock
    private CoreTimezoneService coreTimezoneService;
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

    private static PaymentPlanModel catalogPlan(int planDay) {
        return PaymentPlanModel.builder()
                .planCode(PLAN).fee(BigDecimal.valueOf(10000)).planDay(planDay).envCount(1)
                .planState("ACTIVE").planGroup("PRODUCT").build();
    }

    private User seller(String type) {
        User user = new User();
        user.setUserName(SELLER);
        user.setType(type);
        user.setEmail("s@example.com");
        return user;
    }

    private PlanWindowRequest windowRequest(String startAt) {
        PlanWindowRequest request = new PlanWindowRequest();
        request.setPlanCode(PLAN);
        request.setStartAt(startAt);
        return request;
    }

    private void catalogReady() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(seller("SELLER")));
        when(paymentPlanService.catalog()).thenReturn(List.of(catalogPlan(3)));
    }

    /** A buying account plus the fixed clock the window calculation reads. */
    private void readyToBuy() {
        catalogReady();
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(NOW.getTime()));
    }

    @Test
    void defaultWindowStartsNowToTheMinuteAndRunsThePlanLength() {
        readyToBuy();

        Result<PlanWindowResponse> result = service.planWindow(SELLER, windowRequest(null));

        assertThat(result.isSuccess()).isTrue();
        // Seconds are dropped: the picker works in minutes, and a default with
        // seconds would be refused as "in the past" the moment it was submitted.
        assertThat(result.getData().getStartAt()).isEqualTo("2026-11-28T10:00");
        assertThat(result.getData().getEndAt()).isEqualTo("2026-12-01T10:00");
        assertThat(result.getData().getPlanDay()).isEqualTo(3);
    }

    @Test
    void chosenStartMovesTheEndWithIt() {
        readyToBuy();

        Result<PlanWindowResponse> result = service.planWindow(SELLER, windowRequest("2026-12-05T09:30"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStartAt()).isEqualTo("2026-12-05T09:30");
        assertThat(result.getData().getEndAt()).isEqualTo("2026-12-08T09:30");
    }

    @Test
    void startInThePastIsRefusedWithTheReason() {
        readyToBuy();

        Result<PlanWindowResponse> result = service.planWindow(SELLER, windowRequest("2026-11-27T09:30"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).contains("earlier than now");
    }

    @Test
    void planThatIsNoLongerOnSaleIsRefused() {
        // The catalog lookup rejects the code before any clock is read.
        catalogReady();

        PlanWindowRequest request = new PlanWindowRequest();
        request.setPlanCode("p-gone");
        Result<PlanWindowResponse> result = service.planWindow(SELLER, request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).contains("no longer on sale");
    }

    @Test
    void accountThatCannotBuyGetsNoWindow() {
        when(coreUserService.findUserEntityByUsername(SELLER)).thenReturn(Optional.of(seller("ADMIN")));

        Result<PlanWindowResponse> result = service.planWindow(SELLER, windowRequest(null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(403);
        assertThat(result.getErrorMessage()).contains("cannot buy plans");
    }

    /**
     * The start the seller confirmed has to reach the order, or the whole popup
     * would be cosmetic.
     */
    @Test
    void confirmedStartTravelsWithTheOrder() {
        catalogReady();
        when(paymentPlanService.assignmentsWithPayments(SELLER)).thenReturn(List.of(), List.of());
        when(coreUserService.findPlanPurpose(SELLER)).thenReturn(null);
        when(paymentPlanService.remainingQuota(SELLER, "PRODUCT")).thenReturn(0);
        when(paymentPlanService.freePlanUsed(SELLER)).thenReturn(false);
        when(paymentPlanService.createSellerPlan(any(), anyString())).thenReturn(paidOrder());

        SelectPlanRequest select = new SelectPlanRequest();
        select.setPlanCode(PLAN);
        select.setStartAt("2026-12-10T08:00");
        service.selectPlan(SELLER, select);

        ArgumentCaptor<CreateSellerPlanRequest> captor = ArgumentCaptor.forClass(CreateSellerPlanRequest.class);
        verify(paymentPlanService).createSellerPlan(captor.capture(), eq("ops-admin"));
        assertThat(captor.getValue().getStartAt()).isEqualTo("2026-12-10T08:00");
    }

    private CreateSellerPlanResponse paidOrder() {
        CreateSellerPlanResponse response = new CreateSellerPlanResponse();
        response.setPaymentCode("pay-1");
        response.setFee(BigDecimal.valueOf(10000));
        response.setPlanDay(3);
        response.setEnvCount(1);
        response.setStatus("CREATE");
        return response;
    }
}