package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.entity.PaymentPlan;
import com.qrpublic.apartment.entity.PaymentPlanUser;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ValidationException;
import com.qrpublic.apartment.repository.PaymentPlanRepository;
import com.qrpublic.apartment.repository.PaymentPlanUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The chosen start: what the order records, who wins at activation, and what an
 * admin correction may change.
 *
 * The rule under test is the one the product asked for - an explicit admin
 * start
 * always wins (today included), a blank one honours the seller's request while
 * it is still ahead, and a start that already slipped by gives way to today so
 * nobody is activated in the past or loses days they paid for.
 */
@ExtendWith(MockitoExtension.class)
class CorePaymentPlanStartWindowTest {

    /** Fixed clock: 2026-11-28 00:00, the day the admin leaves the field alone. */
    private static final Timestamp TODAY = Timestamp.valueOf(LocalDate.of(2026, 11, 28).atStartOfDay());

    @Mock
    private PaymentPlanRepository paymentPlanRepository;
    @Mock
    private PaymentPlanUserRepository paymentPlanUserRepository;
    @Mock
    private CoreUserService coreUserService;
    @Mock
    private CoreTimezoneService coreTimezoneService;
    @InjectMocks
    private CorePaymentPlanService service;

    private static PaymentPlan plan(String code, long fee, int days) {
        PaymentPlan plan = new PaymentPlan();
        plan.setPlanCode(code);
        plan.setFee(BigDecimal.valueOf(fee));
        plan.setPlanDay(days);
        plan.setEnvCount(1);
        plan.setPlanState("ACTIVE");
        plan.setPlanGroup("PRODUCT");
        return plan;
    }

    private static User seller() {
        User user = new User();
        user.setUserName("seller-1");
        user.setName("Seller One");
        return user;
    }

    private static PaymentPlanUser assigning(PaymentPlan plan, Timestamp requestedStart) {
        PaymentPlanUser assignment = new PaymentPlanUser();
        assignment.setPlanUserCode("pu-1");
        assignment.setUser(seller());
        assignment.setPlan(plan);
        assignment.setPlanState("CREATE");
        assignment.setEnvQuota(plan.getEnvCount());
        assignment.setUsedCount(0);
        assignment.setRequestedStartAt(requestedStart);
        return assignment;
    }

    private void clockIsToday() {
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));
    }

    // ------------------------------------------------------- opening the order

    @Test
    void chosenStartIsStoredAndProjectedOnTheOrder() {
        PaymentPlan plan = plan("p-10", 10000, 3);
        when(paymentPlanRepository.findByPlanCode("p-10")).thenReturn(Optional.of(plan));
        when(coreUserService.getUserEntityByUsername("seller-1")).thenReturn(seller());
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel order = service.createAssignment("seller-1", "p-10", "2026-12-05T09:30");

        assertThat(order.getPlanState()).isEqualTo("CREATE");
        assertThat(order.getRequestedStartAt()).startsWith("2026-12-05 09:30");
        // Projected, so the payment page and the admin queue show the window the
        // seller asked for instead of the day the order happened to be created.
        assertThat(order.getActiveDate()).startsWith("2026-12-05 09:30");
        assertThat(order.getEndedDate()).startsWith("2026-12-08 09:30");
    }

    /**
     * A start that slipped by while the seller sat on the payment screen must not
     * turn into a refused purchase: the plan simply starts when the money is
     * confirmed, which is where activation would land anyway.
     */
    @Test
    void startThatSlippedByIsDroppedRatherThanRefused() {
        PaymentPlan plan = plan("p-10", 10000, 3);
        when(paymentPlanRepository.findByPlanCode("p-10")).thenReturn(Optional.of(plan));
        when(coreUserService.getUserEntityByUsername("seller-1")).thenReturn(seller());
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel order = service.createAssignment("seller-1", "p-10", "2026-11-27T09:30");

        assertThat(order.getPlanState()).isEqualTo("CREATE");
        assertThat(order.getRequestedStartAt()).isEmpty();
        assertThat(order.getActiveDate()).isEmpty();
    }

    // ----------------------------------------------------------- activation

    /**
     * The regression the admin screen was rebuilt for: a start on today at a
     * specific time is a deliberate choice and must not be mistaken for the
     * untouched field it used to be.
     */
    @Test
    void explicitStartOnTodayWinsOverTheSellersRequest() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3),
                Timestamp.valueOf("2026-12-05 09:30:00"));
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel activated = service.activate("pu-1", "2026-11-28T14:00");

        assertThat(activated.getActiveDate()).startsWith("2026-11-28 14:00");
        assertThat(activated.getEndedDate()).startsWith("2026-12-01 14:00");
        assertThat(activated.getPlanState()).isEqualTo("ACTIVE");
    }

    @Test
    void futureChosenStartWinsWhenNoStartIsGiven() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3),
                Timestamp.valueOf("2026-12-05 09:30:00"));
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel activated = service.activate("pu-1", null);

        assertThat(activated.getActiveDate()).startsWith("2026-12-05 09:30");
    }

    /**
     * The transfer arrived after the day the seller asked for, and the admin gave
     * no start: the plan starts today so the seller still gets the full length,
     * never a window that begins in the past.
     */
    @Test
    void chosenStartAlreadyPassedFallsBackToToday() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3),
                Timestamp.valueOf("2026-11-20 09:30:00"));
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel activated = service.activate("pu-1", null);

        assertThat(activated.getActiveDate()).startsWith("2026-11-28");
        assertThat(activated.getEndedDate()).startsWith("2026-12-01");
    }

    @Test
    void explicitAdminStartOverridesTheChosenStart() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3),
                Timestamp.valueOf("2026-12-05 09:30:00"));
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel activated = service.activate("pu-1", "2026-12-10");

        assertThat(activated.getActiveDate()).startsWith("2026-12-10");
        assertThat(activated.getEndedDate()).startsWith("2026-12-13");
    }

    /** Admin-assigned rows carry no request and behave exactly as before. */
    @Test
    void rowWithoutARequestKeepsTheAdminStart() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3), null);
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel activated = service.activate("pu-1", "2026-12-10");

        assertThat(activated.getActiveDate()).startsWith("2026-12-10");
        assertThat(activated.getEndedDate()).startsWith("2026-12-13");
    }

    // ------------------------------------------------- moving a start by admin

    @Test
    void waitingOrderTakesTheNewStartAndKeepsItsQueueState() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3), null);
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel updated = service.changeStartWindow("pu-1", "2026-12-05T09:30");

        assertThat(updated.getPlanState()).isEqualTo("CREATE");
        assertThat(updated.getRequestedStartAt()).startsWith("2026-12-05 09:30");
        // Projected, so the payment page and the queue read the new dates.
        assertThat(updated.getActiveDate()).startsWith("2026-12-05 09:30");
        assertThat(updated.getEndedDate()).startsWith("2026-12-08 09:30");
    }

    /**
     * A running plan keeps the days that were paid for: only the start moves and
     * the end follows, which is the decision the admin screen was built on.
     */
    @Test
    void runningPlanMovesAndKeepsItsLength() {
        PaymentPlan plan = plan("p-10", 10000, 3);
        PaymentPlanUser assignment = assigning(plan, Timestamp.valueOf("2026-12-05 09:30:00"));
        assignment.setPlanState("ACTIVE");
        assignment.setUsedCount(1);
        assignment.setActiveDate(Timestamp.valueOf("2026-12-05 09:30:00"));
        assignment.setEndedDate(Timestamp.valueOf("2026-12-08 09:30:00"));
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel updated = service.changeStartWindow("pu-1", "2026-12-10T08:00");

        assertThat(updated.getPlanState()).isEqualTo("ACTIVE");
        assertThat(updated.getActiveDate()).startsWith("2026-12-10 08:00");
        assertThat(updated.getEndedDate()).startsWith("2026-12-13 08:00");
        assertThat(updated.getUsedCount()).isEqualTo(1);
    }

    @Test
    void waitingOrderCannotBeBackdated() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3), null);
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();

        assertThatThrownBy(() -> service.changeStartWindow("pu-1", "2026-11-01T09:00"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot start in the past");
    }

    @Test
    void windowThatWouldAlreadyHaveEndedIsRefused() {
        PaymentPlan plan = plan("p-10", 10000, 3);
        PaymentPlanUser assignment = assigning(plan, null);
        assignment.setPlanState("ACTIVE");
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));
        clockIsToday();

        assertThatThrownBy(() -> service.changeStartWindow("pu-1", "2026-11-01T09:00"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already in the past");
    }

    /** An order row is money for another plan, never a window of its own. */
    @Test
    void orderRowStartCannotBeChanged() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3), null);
        assignment.setExtendDays(5);
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.changeStartWindow("pu-1", "2026-12-05T09:30"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("order for another plan");
    }

    @Test
    void deactivatedPlanStartCannotBeChanged() {
        PaymentPlanUser assignment = assigning(plan("p-10", 10000, 3), null);
        assignment.setPlanState("DEACTIVE");
        when(paymentPlanUserRepository.findByPlanUserCode("pu-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.changeStartWindow("pu-1", "2026-12-05T09:30"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("waiting or running plan");
    }
}
