package com.qrpublic.apartment.domain.paymentplan;

import com.qrpublic.apartment.core.model.LinkModel;
import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CorePaymentPlanService;
import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.service.LinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A 0 fee plan has no money to wait for, so it must start running the moment it
 * is assigned instead of sitting in the "waiting for pay" queue. This is the
 * behaviour the free-plan offer depends on, and it is easy to get subtly wrong:
 * a paid plan must keep going through the admin exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPlanServiceFreeAutoApproveTest {

    @Mock
    private CorePaymentPlanService corePaymentPlanService;
    @Mock
    private CorePaymentService corePaymentService;
    @Mock
    private CoreNotificationService coreNotificationService;
    @Mock
    private LinkService linkService;
    @InjectMocks
    private PaymentPlanService service;

    private static PlanUserModel assignment(BigDecimal fee) {
        return PlanUserModel.builder()
                .planUserCode("pu-1")
                .username("seller-1")
                .fee(fee)
                .planDay(5)
                .envQuota(1)
                .planState("CREATE")
                .build();
    }

    private static PaymentModel payment() {
        return PaymentModel.builder()
                .paymentCode("pay-1")
                .planUserCode("pu-1")
                .status("CREATE")
                .build();
    }

    private void stubPaymentUrl() {
        when(linkService.generateSecureUrl(anyString(), anyMap(), anyLong()))
                .thenReturn(new LinkModel("context", "token-1", null, null));
    }

    private CreateSellerPlanResponse create(BigDecimal fee) {
        CreateSellerPlanRequest request = new CreateSellerPlanRequest();
        request.setUsername("seller-1");
        request.setPlanCode("p-1");
        return service.createSellerPlan(request, "admin-1");
    }

    @Test
    void freePlanIsConfirmedOnTheSpot() {
        PlanUserModel created = assignment(BigDecimal.ZERO);
        when(corePaymentPlanService.createAssignment("seller-1", "p-1", null)).thenReturn(created);
        when(corePaymentService.createPayment("pu-1", BigDecimal.ZERO, "admin-1")).thenReturn(payment());
        stubPaymentUrl();
        // The automatic confirmation reuses the admin path, so its reads must work.
        when(corePaymentService.findPaymentsByPlanUserCodes(List.of("pu-1"))).thenReturn(List.of(payment()));
        when(corePaymentPlanService.findAssignmentByCode("pu-1")).thenReturn(created);
        when(corePaymentPlanService.activate(eq("pu-1"), isNull())).thenReturn(PlanUserModel.builder()
                .planUserCode("pu-1").username("seller-1").activeDate("2026-11-28").endedDate("2026-12-03")
                .envQuota(1).planState("ACTIVE").build());

        CreateSellerPlanResponse response = create(BigDecimal.ZERO);

        assertThat(response.getStatus()).isEqualTo("PAID");
        verify(corePaymentService).markPaid(eq("pay-1"), eq("auto-approved free plan"), eq("admin-1"));
        // The seller is told the plan is running, exactly as after a paid mark.
        verify(coreNotificationService).notifyUsernames(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    /**
     * The regression this guards: if the free branch were written loosely the
     * paid flow would auto-confirm too, and money would be recorded as received
     * before any admin saw the transfer.
     */
    @Test
    void paidPlanStillWaitsForTheAdmin() {
        BigDecimal fee = BigDecimal.valueOf(15000);
        when(corePaymentPlanService.createAssignment("seller-1", "p-1", null)).thenReturn(assignment(fee));
        when(corePaymentService.createPayment("pu-1", fee, "admin-1")).thenReturn(payment());
        stubPaymentUrl();

        CreateSellerPlanResponse response = create(fee);

        assertThat(response.getStatus()).isEqualTo("CREATE");
        verify(corePaymentService, never()).markPaid(anyString(), anyString(), anyString());
        verify(corePaymentPlanService, never()).activate(anyString(), any());
    }

    @Test
    void freePlanStillGetsAPaymentRecordForTheAuditTrail() {
        when(corePaymentPlanService.createAssignment("seller-1", "p-1", null)).thenReturn(assignment(BigDecimal.ZERO));
        when(corePaymentService.createPayment("pu-1", BigDecimal.ZERO, "admin-1")).thenReturn(payment());
        stubPaymentUrl();
        when(corePaymentService.findPaymentsByPlanUserCodes(List.of("pu-1"))).thenReturn(List.of(payment()));
        when(corePaymentPlanService.findAssignmentByCode("pu-1")).thenReturn(assignment(BigDecimal.ZERO));
        when(corePaymentPlanService.activate(eq("pu-1"), isNull())).thenReturn(assignment(BigDecimal.ZERO));

        CreateSellerPlanResponse response = create(BigDecimal.ZERO);

        // Zero does not mean invisible: the money side keeps a row and a link.
        assertThat(response.getPaymentCode()).isEqualTo("pay-1");
        assertThat(response.getPaymentUrl()).contains("token=token-1");
        verify(corePaymentService).updatePaymentUrl(eq("pay-1"), any());
    }
}
