package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.entity.SellerPaymentPlan;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.repository.SellerPaymentPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A seller package counts either calendar days or bookings, chosen at creation.
 * Counting days used to mean "one booking per date" and a second slot on the
 * same date was refused, so a 7-day buyer could not take a morning and an
 * afternoon class. These cases pin the two units apart.
 */
@ExtendWith(MockitoExtension.class)
class CoreSellerPaymentPlanQuotaTest {

    @Mock
    private SellerPaymentPlanRepository sellerPaymentPlanRepository;
    @Mock
    private SaleEnvironmentRepository saleEnvironmentRepository;
    @InjectMocks
    private CoreSellerPaymentPlanService service;

    private SellerPaymentPlan plan(String quotaType, int total, int usedDays, int usedBookings) {
        SellerPaymentPlan plan = new SellerPaymentPlan();
        plan.setSellerPlanId(7L);
        plan.setSellerPlanCode("plan-1");
        com.qrpublic.apartment.entity.SaleEnvironment env = new com.qrpublic.apartment.entity.SaleEnvironment();
        env.setEnvId("env-1");
        plan.setSaleEnvironment(env);
        plan.setQuotaType(quotaType);
        plan.setTotalDays(total);
        plan.setUsedDays(usedDays);
        plan.setUsedBookings(usedBookings);
        plan.setState("ACTIVE");
        plan.setAmountVnd(new BigDecimal("15000"));
        return plan;
    }

    @Test
    void daysPlanChargesADayOnlyForANewDate() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 1, 1);
        when(sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate("plan-1")).thenReturn(Optional.of(plan));

        // second slot on the same date: no new day, but the booking count grows
        service.consumeQuota("plan-1", false);

        assertThat(plan.getUsedDays()).isEqualTo(1);
        assertThat(plan.getUsedBookings()).isEqualTo(2);
        verify(sellerPaymentPlanRepository).save(plan);
    }

    @Test
    void daysPlanChargesBothCountersOnAFreshDate() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 1, 2);
        when(sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate("plan-1")).thenReturn(Optional.of(plan));

        service.consumeQuota("plan-1", true);

        assertThat(plan.getUsedDays()).isEqualTo(2);
        assertThat(plan.getUsedBookings()).isEqualTo(3);
    }

    @Test
    void daysPlanRunsOutOnDatesNotBookings() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 2, 2, 9);
        when(sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate("plan-1")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.consumeQuota("plan-1", true))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("no days left (2/2)");
    }

    @Test
    void bookingsPlanRunsOutPerBookingEvenOnTheSameDate() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_BOOKINGS, 3, 1, 3);
        when(sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate("plan-1")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.consumeQuota("plan-1", false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("no bookings left (3/3)");
    }

    @Test
    void cancellingTheLastBookingOfADateReturnsTheDay() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 2, 3);
        when(sellerPaymentPlanRepository.findById(7L)).thenReturn(Optional.of(plan));

        service.refundQuota(7L, true);

        assertThat(plan.getUsedDays()).isEqualTo(1);
        assertThat(plan.getUsedBookings()).isEqualTo(2);
    }

    @Test
    void cancellingOneOfSeveralBookingsOfADateKeepsTheDay() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 2, 3);
        when(sellerPaymentPlanRepository.findById(7L)).thenReturn(Optional.of(plan));

        service.refundQuota(7L, false);

        assertThat(plan.getUsedDays()).isEqualTo(2);
        assertThat(plan.getUsedBookings()).isEqualTo(2);
    }

    @Test
    void modelReportsRemainingInTheUnitsThePlanCounts() {
        SellerPaymentPlan days = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 2, 5);
        SellerPaymentPlan bookings = plan(SellerPaymentPlan.QUOTA_BOOKINGS, 7, 2, 5);
        when(sellerPaymentPlanRepository.findByEnvIdAndBuyerUsername("env-1", "buyer"))
                .thenReturn(Optional.of(days), Optional.of(bookings));

        SellerPlanModel daysModel = service.findByEnvAndBuyer("env-1", "buyer");
        SellerPlanModel bookingsModel = service.findByEnvAndBuyer("env-1", "buyer");

        // five bookings over two dates: 5 days left for a DAYS package, 2 left
        // for a BOOKINGS package
        assertThat(daysModel.getRemainingDays()).isEqualTo(5);
        assertThat(bookingsModel.getRemainingDays()).isEqualTo(2);
        assertThat(daysModel.getUsedBookings()).isEqualTo(5);
    }

    @Test
    void unitCannotChangeOnceBookingsHaveStarted() {
        SellerPaymentPlan plan = plan(SellerPaymentPlan.QUOTA_DAYS, 7, 1, 1);
        when(sellerPaymentPlanRepository.findBySellerPlanCode("plan-1")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.updatePlan("plan-1", 7, null, null, null,
                SellerPaymentPlan.QUOTA_BOOKINGS))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("unit cannot change");
    }
}
