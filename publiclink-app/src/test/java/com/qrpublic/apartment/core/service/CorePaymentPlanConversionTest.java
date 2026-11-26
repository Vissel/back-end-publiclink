package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PlanGateModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.entity.PaymentPlan;
import com.qrpublic.apartment.entity.PaymentPlanUser;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ValidationException;
import com.qrpublic.apartment.repository.PaymentPlanRepository;
import com.qrpublic.apartment.repository.PaymentPlanUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conversion orders and the group gate, the two rules that carry real money:
 * what a conversion costs, and which plan is allowed to pay for which link.
 */
@ExtendWith(MockitoExtension.class)
class CorePaymentPlanConversionTest {

    private static final Timestamp TODAY = Timestamp.valueOf(LocalDate.of(2026, 11, 27).atStartOfDay());

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

    private static PaymentPlan plan(String code, long fee, int days, int envCount, String group) {
        PaymentPlan plan = new PaymentPlan();
        plan.setPlanCode(code);
        plan.setFee(BigDecimal.valueOf(fee));
        plan.setPlanDay(days);
        plan.setEnvCount(envCount);
        plan.setPlanState("ACTIVE");
        plan.setPlanGroup(group);
        return plan;
    }

    private static PaymentPlanUser assignment(String code, PaymentPlan plan, String state) {
        User user = new User();
        user.setUserName("seller-1");
        user.setName("Seller One");

        PaymentPlanUser assignment = new PaymentPlanUser();
        assignment.setPlanUserCode(code);
        assignment.setUser(user);
        assignment.setPlan(plan);
        assignment.setPlanState(state);
        assignment.setEnvQuota(plan.getEnvCount());
        assignment.setUsedCount(0);
        assignment.setActiveDate(TODAY);
        assignment.setEndedDate(Timestamp.valueOf(TODAY.toLocalDateTime().plusDays(plan.getPlanDay())));
        return assignment;
    }

    private void clockIsToday() {
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));
    }

    // ------------------------------------------------ opening a conversion order

    @Test
    void orderCarriesTheDifferenceAndNoQuotaOfItsOwn() {
        PaymentPlan current = plan("p-15", 15000, 7, 1, "BOOKING");
        PaymentPlan target = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlanUser source = assignment("src-1", current, "ACTIVE");

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        when(paymentPlanRepository.findByPlanCode("p-35")).thenReturn(Optional.of(target));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel order = service.createConversionAssignment("src-1", "p-35");

        // The difference, never the full price of the better plan.
        assertThat(order.getFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(order.getConvertFeeDelta()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(order.getConvertsPlanUserCode()).isEqualTo("src-1");
        assertThat(order.getPlanState()).isEqualTo("CREATE");
        // It buys no environment slot of its own, so it can never be mistaken
        // for a subscription that hands out extra links.
        assertThat(order.getEnvQuota()).isZero();
        assertThat(order.getPlanDay()).isEqualTo(30);
    }

    @Test
    void cheaperTargetCannotOpenAnOrder() {
        PaymentPlan current = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlan cheaper = plan("p-5", 5000, 1, 1, "BOOKING");
        PaymentPlanUser source = assignment("src-1", current, "ACTIVE");

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        when(paymentPlanRepository.findByPlanCode("p-5")).thenReturn(Optional.of(cheaper));

        assertThatThrownBy(() -> service.createConversionAssignment("src-1", "p-5"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nothing to pay for");
        verify(paymentPlanUserRepository, never()).save(any());
    }

    @Test
    void conversionNeverCrossesGroups() {
        PaymentPlan current = plan("p-15", 15000, 7, 1, "BOOKING");
        PaymentPlan productPlan = plan("p-prod", 30000, 30, 5, "PRODUCT");
        PaymentPlanUser source = assignment("src-1", current, "ACTIVE");

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        when(paymentPlanRepository.findByPlanCode("p-prod")).thenReturn(Optional.of(productPlan));

        assertThatThrownBy(() -> service.createConversionAssignment("src-1", "p-prod"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BOOKING").hasMessageContaining("PRODUCT");
        verify(paymentPlanUserRepository, never()).save(any());
    }

    /**
     * An order row is already spent money; converting it again would double-bill.
     */
    @Test
    void extensionOrderIsNotConvertible() {
        PaymentPlan current = plan("p-15", 15000, 7, 1, "BOOKING");
        PaymentPlanUser extension = assignment("src-1", current, "ACTIVE");
        extension.setExtendDays(5);

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(extension));

        // The order-row guard fires before the target is even looked up, so no
        // catalog stub belongs here.
        assertThatThrownBy(() -> service.createConversionAssignment("src-1", "p-35"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("order row");
    }

    // ------------------------------------------------------ applying a conversion

    @Test
    void payingRebuildsThePlanWithTheTargetWindowAndQuota() {
        PaymentPlan oldPlan = plan("p-15", 15000, 7, 1, "BOOKING");
        PaymentPlan newPlan = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlanUser source = assignment("src-1", oldPlan, "ACTIVE");
        source.setUsedCount(1);
        PaymentPlanUser order = assignment("ord-1", newPlan, "CREATE");
        order.setConvertsPlanUserCode("src-1");
        order.setConvertFeeDelta(BigDecimal.valueOf(20000));

        when(paymentPlanUserRepository.findByPlanUserCode("ord-1")).thenReturn(Optional.of(order));
        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel rebuilt = service.applyConversion("ord-1");

        assertThat(rebuilt.getPlanGroup()).isEqualTo("BOOKING");
        assertThat(rebuilt.getEnvQuota()).isEqualTo(5);
        // Links already spent are forgiven: the target's full quota is granted.
        assertThat(rebuilt.getUsedCount()).isZero();
        assertThat(rebuilt.getPlanDay()).isEqualTo(30);
        // Restart-window semantics, not "keep the remaining days".
        assertThat(rebuilt.getActiveDate()).startsWith("2026-11-27");
        assertThat(rebuilt.getEndedDate()).startsWith("2026-12-27");
        assertThat(order.getPlanState()).isEqualTo("CONVERTED");
    }

    /**
     * Approving the order row instead of confirming its payment would hand out a
     * plan window of its own and leave the converted plan untouched, so the
     * ordinary approve path has to refuse it.
     */
    @Test
    void conversionOrderCannotBeApprovedLikeAPlan() {
        PaymentPlan target = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlanUser order = assignment("ord-1", target, "CREATE");
        order.setConvertsPlanUserCode("src-1");

        when(paymentPlanUserRepository.findByPlanUserCode("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.activate("ord-1", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("conversion order");
    }

    @Test
    void nothingOwedConvertsWithoutAnOrder() {
        PaymentPlan current = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlan cheaper = plan("p-5", 5000, 1, 1, "BOOKING");
        PaymentPlanUser source = assignment("src-1", current, "ACTIVE");

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        when(paymentPlanRepository.findByPlanCode("p-5")).thenReturn(Optional.of(cheaper));
        clockIsToday();
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanUserModel rebuilt = service.convertWithoutPayment("src-1", "p-5");

        assertThat(rebuilt.getEnvQuota()).isEqualTo(1);
        assertThat(rebuilt.getPlanDay()).isEqualTo(1);
        assertThat(rebuilt.getPlanState()).isEqualTo("ACTIVE");
        // No order row: nothing was owed, so nothing may appear in the pay queue.
        assertThat(rebuilt.getConvertsPlanUserCode()).isNull();
    }

    @Test
    void dearerTargetIsRefusedByTheNoPaymentPath() {
        PaymentPlan current = plan("p-15", 15000, 7, 1, "BOOKING");
        PaymentPlan dearer = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlanUser source = assignment("src-1", current, "ACTIVE");

        when(paymentPlanUserRepository.findByPlanUserCode("src-1")).thenReturn(Optional.of(source));
        when(paymentPlanRepository.findByPlanCode("p-35")).thenReturn(Optional.of(dearer));

        assertThatThrownBy(() -> service.convertWithoutPayment("src-1", "p-35"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has to be paid for first");
        verify(paymentPlanUserRepository, never()).save(any());
    }

    // ------------------------------------------------------------- the group gate

    /**
     * The distinction the whole split is about: money tied up in the other group
     * is not "no plan", because buying more of what they already have would not
     * unblock the seller either.
     */
    @Test
    void usablePlansOfTheOtherGroupReadAsWrongGroup() {
        PaymentPlan productPlan = plan("p-prod", 30000, 30, 5, "PRODUCT");
        PaymentPlanUser productAssignment = assignment("src-1", productPlan, "ACTIVE");

        when(paymentPlanUserRepository.findActiveByUserNameAndGroup("seller-1", "BOOKING"))
                .thenReturn(List.of());
        when(paymentPlanUserRepository.findActiveByUserName("seller-1"))
                .thenReturn(List.of(productAssignment));
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));

        PlanGateModel gate = service.evaluate("seller-1", "BOOKING");

        assertThat(gate.isAllowed()).isFalse();
        assertThat(gate.getReason()).isEqualTo(PlanGateModel.REASON_WRONG_GROUP);
        assertThat(gate.getMessage()).contains("PRODUCT").contains("BOOKING");
        assertThat(gate.getPlanGroup()).isEqualTo("BOOKING");
    }

    /** A brand new account must still be pointed at the catalog, not at a group. */
    @Test
    void accountWithNoPlansAtAllStaysNoPlan() {
        when(paymentPlanUserRepository.findActiveByUserNameAndGroup("seller-1", "BOOKING"))
                .thenReturn(List.of());
        when(paymentPlanUserRepository.findActiveByUserName("seller-1")).thenReturn(List.of());
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));

        PlanGateModel gate = service.evaluate("seller-1", "BOOKING");

        assertThat(gate.isAllowed()).isFalse();
        assertThat(gate.getReason()).isEqualTo(PlanGateModel.REASON_NO_PLAN);
    }

    @Test
    void usablePlanOfTheRightGroupIsAllowedAndNamesItsGroup() {
        PaymentPlan bookingPlan = plan("p-15", 15000, 7, 3, "BOOKING");
        PaymentPlanUser usable = assignment("src-1", bookingPlan, "ACTIVE");

        when(paymentPlanUserRepository.findActiveByUserNameAndGroup("seller-1", "BOOKING"))
                .thenReturn(List.of(usable));
        when(paymentPlanUserRepository.findUsableActiveByUserNameAndGroup("seller-1", TODAY, "BOOKING"))
                .thenReturn(List.of(usable));
        when(paymentPlanUserRepository.findActiveByUserName("seller-1")).thenReturn(List.of(usable));
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));

        PlanGateModel gate = service.evaluate("seller-1", "BOOKING");

        assertThat(gate.isAllowed()).isTrue();
        assertThat(gate.getPlanGroup()).isEqualTo("BOOKING");
        assertThat(gate.getRemaining()).isEqualTo(3);
    }

    /**
     * A product plan must not be spendable on a booking link even though the
     * account plainly has usable quota.
     */
    @Test
    void consumingRefusesToSpendTheOtherGroupsQuota() {
        when(paymentPlanUserRepository.findUsableActiveByUserNameAndGroupForUpdate("seller-1", TODAY, "BOOKING"))
                .thenReturn(List.of());
        when(paymentPlanUserRepository.findActiveByUserNameAndGroup("seller-1", "BOOKING"))
                .thenReturn(List.of());
        when(paymentPlanUserRepository.findActiveByUserName("seller-1"))
                .thenReturn(List.of(assignment("src-1", plan("p-prod", 30000, 30, 5, "PRODUCT"), "ACTIVE")));
        when(coreTimezoneService.getNow()).thenReturn(new java.util.Date(TODAY.getTime()));

        PlanGateModel gate = service.consumeOne("seller-1", "BOOKING");

        assertThat(gate.isAllowed()).isFalse();
        assertThat(gate.getReason()).isEqualTo(PlanGateModel.REASON_WRONG_GROUP);
        verify(paymentPlanUserRepository, never()).save(any());
        verify(paymentPlanUserRepository, never()).findUsableActiveByUserNameForUpdate(any(), any());
    }

    @Test
    void consumingSpendsTheRequestedGroupRow() {
        PaymentPlan bookingPlan = plan("p-15", 15000, 7, 3, "BOOKING");
        PaymentPlanUser usable = assignment("src-1", bookingPlan, "ACTIVE");
        clockIsToday();
        when(paymentPlanUserRepository.findUsableActiveByUserNameAndGroupForUpdate("seller-1", TODAY, "BOOKING"))
                .thenReturn(List.of(usable));
        when(paymentPlanUserRepository.save(any(PaymentPlanUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlanGateModel gate = service.consumeOne("seller-1", "BOOKING");

        assertThat(gate.isAllowed()).isTrue();
        assertThat(gate.getRemaining()).isEqualTo(2);
        assertThat(usable.getUsedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------- unmapped legacy rows

    /**
     * Rows written before changeset 036 carry no group at all. Reading them as
     * anything but PRODUCT would hide a seller's own plan from them.
     */
    @Test
    void planWithoutStoredGroupReadsAsProduct() {
        PaymentPlan legacy = plan("p-old", 3000, 1, 1, null);
        PaymentPlanUser source = assignment("src-1", legacy, "ACTIVE");

        PlanUserModel model = service.toAssignmentModel(source);

        assertThat(model.getPlanGroup()).isEqualTo("PRODUCT");
    }

    @Test
    void conversionOrderReportsTheDeltaAsItsFee() {
        PaymentPlan target = plan("p-35", 35000, 30, 5, "BOOKING");
        PaymentPlanUser order = assignment("ord-1", target, "CREATE");
        order.setConvertsPlanUserCode("src-1");
        order.setConvertFeeDelta(BigDecimal.valueOf(20000));
        order.setEnvQuota(0);

        PlanUserModel model = service.toAssignmentModel(order);

        // The queue tables must show what is due, not the target's full price.
        assertThat(model.getFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(model.getPlanDay()).isEqualTo(30);
    }
}
