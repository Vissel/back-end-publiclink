package com.qrpublic.apartment.domain.paymentplan;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.core.service.CoreTransferInfoService;
import com.qrpublic.apartment.paymentplan.response.PaymentInfoResponse;
import com.qrpublic.apartment.service.LinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * What the payment page promises. A seller who chose a later start must not
 * read
 * "you get the plan from today", because that is not the window they bought.
 */
@ExtendWith(MockitoExtension.class)
class PaymentDomainServiceWindowTest {

    private static final String TOKEN = "tok";

    @Mock
    private LinkService linkService;
    @Mock
    private CorePaymentService corePaymentService;
    @Mock
    private CoreTransferInfoService coreTransferInfoService;
    @Mock
    private CoreNotificationService coreNotificationService;
    @InjectMocks
    private PaymentDomainService service;

    private PaymentModel payment(String requestedStart, String requestedEnd) {
        return PaymentModel.builder()
                .paymentCode("pay-1")
                .amount(BigDecimal.valueOf(10000))
                .currency("VND")
                .status("CREATE")
                .plan(PaymentPlanModel.builder()
                        .planCode("p-10").fee(BigDecimal.valueOf(10000)).planDay(3).envCount(1)
                        .planState("ACTIVE").planGroup("PRODUCT").build())
                .requestedStartAt(requestedStart)
                .requestedEndAt(requestedEnd)
                .build();
    }

    private void tokenResolves(PaymentModel payment) {
        when(linkService.validateLink(TOKEN)).thenReturn(true);
        when(linkService.extractClaimByKey(TOKEN, LinkConstant.PARAM_PAYMENT_CODE)).thenReturn("pay-1");
        when(corePaymentService.findPaymentByCode("pay-1")).thenReturn(payment);
    }

    @Test
    void pageShowsTheWindowTheSellerAskedFor() {
        tokenResolves(payment("2026-12-05 09:30:00", "2026-12-08 09:30:00"));

        PaymentInfoResponse info = service.paymentInfo(TOKEN);

        assertThat(info.getEstimatedActiveDate()).isEqualTo("2026-12-05 09:30:00");
        assertThat(info.getEstimatedEndDate()).isEqualTo("2026-12-08 09:30:00");
    }

    /** Rows without a request keep promising today plus the plan length. */
    @Test
    void pageFallsBackToTodayForAPlainOrder() {
        tokenResolves(payment("", null));

        PaymentInfoResponse info = service.paymentInfo(TOKEN);

        LocalDate today = LocalDate.now();
        assertThat(info.getEstimatedActiveDate()).isEqualTo(today.toString());
        assertThat(info.getEstimatedEndDate()).isEqualTo(today.plusDays(3).toString());
    }
}