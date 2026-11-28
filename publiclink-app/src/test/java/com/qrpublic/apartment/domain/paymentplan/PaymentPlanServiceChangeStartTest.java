package com.qrpublic.apartment.domain.paymentplan;

import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CorePaymentPlanService;
import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.service.LinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin acting on a start: a running plan tells its seller (links may
 * already
 * be shared), while a row still waiting for payment has nobody to tell yet.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPlanServiceChangeStartTest {

    private static final String START = "2026-12-10T08:00";

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

    private PlanUserModel assignment(String state) {
        return PlanUserModel.builder()
                .planUserCode("pu-1")
                .username("seller-1")
                .planState(state)
                .activeDate("2026-12-10 08:00:00")
                .endedDate("2026-12-13 08:00:00")
                .build();
    }

    @Test
    void runningPlanChangeNotifiesTheSeller() {
        when(corePaymentPlanService.changeStartWindow("pu-1", START)).thenReturn(assignment("ACTIVE"));

        int count = service.changeStart(List.of("pu-1"), START, "admin-1");

        assertThat(count).isEqualTo(1);
        verify(coreNotificationService).notifyUsernames(eq(List.of("seller-1")), eq("Plan window updated"),
                anyString(), eq("PLAN"), eq("admin-1"), eq("PLAN"), eq("pu-1"));
    }

    @Test
    void waitingOrderChangeStaysSilent() {
        when(corePaymentPlanService.changeStartWindow("pu-1", START)).thenReturn(assignment("CREATE"));

        int count = service.changeStart(List.of("pu-1"), START, "admin-1");

        assertThat(count).isEqualTo(1);
        verify(coreNotificationService, never()).notifyUsernames(
                org.mockito.ArgumentMatchers.anyList(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }
}