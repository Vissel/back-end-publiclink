package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.entity.PaymentPlan;
import com.qrpublic.apartment.entity.PaymentPlanUser;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.PaymentPlanRepository;
import com.qrpublic.apartment.repository.PaymentPlanUserRepository;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Core layer for payment plans: catalog entries and per-user plan assignments.
 * The only layer touching PaymentPlan / PaymentPlanUser entities and
 * repositories. Callers above the core layer exchange codes and usernames only;
 * entities are resolved inside and converted to core models on the way out.
 */
@Slf4j
@Service
public class CorePaymentPlanService {

    @Autowired
    private PaymentPlanRepository paymentPlanRepository;

    @Autowired
    private PaymentPlanUserRepository paymentPlanUserRepository;

    @Autowired
    private CoreUserService coreUserService;

    // ---------------------------------------------------------------- catalog

    @Transactional(readOnly = true)
    public List<PaymentPlanModel> findActivePlans() {
        return paymentPlanRepository.findAllActive().stream()
                .map(this::toPlanModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentPlanModel findPlanByCode(String planCode) {
        return paymentPlanRepository.findByPlanCode(planCode)
                .map(this::toPlanModel)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan not found: " + planCode));
    }

    // ------------------------------------------------------------ assignments

    /**
     * Assign an existing catalog plan to a user. State starts at CREATE.
     */
    @Transactional
    public PlanUserModel createAssignment(String username, String planCode) {
        PaymentPlan plan = paymentPlanRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan not found: " + planCode));
        return toAssignmentModel(saveAssignment(coreUserService.getUserEntityByUsername(username), plan));
    }

    /**
     * Assign an inline-defined plan. Reuses an identical ACTIVE catalog entry
     * when it exists, otherwise appends a new one (3000 | 1 day | 1 link ...).
     */
    @Transactional
    public PlanUserModel createAssignmentWithNewPlan(String username, BigDecimal fee, int planDay,
            int envCount, String description) {
        PaymentPlan plan = paymentPlanRepository.findActiveByFeeAndDayAndEnv(fee, planDay, envCount)
                .orElseGet(() -> {
                    PaymentPlan created = new PaymentPlan();
                    created.setPlanCode(UUID.randomUUID().toString());
                    created.setFee(fee);
                    created.setPlanDay(planDay);
                    created.setEnvCount(envCount);
                    created.setDescription(description);
                    created.setPlanState("ACTIVE");
                    log.info("Appending payment plan catalog entry fee={} day={} env={}", fee, planDay, envCount);
                    return paymentPlanRepository.save(created);
                });
        return toAssignmentModel(saveAssignment(coreUserService.getUserEntityByUsername(username), plan));
    }

    private PaymentPlanUser saveAssignment(User user, PaymentPlan plan) {
        PaymentPlanUser assignment = new PaymentPlanUser();
        assignment.setPlanUserCode(UUID.randomUUID().toString());
        assignment.setUser(user);
        assignment.setPlan(plan);
        assignment.setEnvQuota(plan.getEnvCount());
        assignment.setUsedCount(0);
        assignment.setPlanState("CREATE");
        return paymentPlanUserRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public PlanUserModel findAssignmentByCode(String planUserCode) {
        return toAssignmentModel(getEntityByCode(planUserCode));
    }

    /**
     * Search / filter / pagination for the setup page tables. state null means
     * ACTIVE (the default first paint), 'ALL' means no state restriction.
     */
    @Transactional(readOnly = true)
    public List<PlanUserModel> findAssignments(String state, String username, Pageable pageable) {
        String effectiveState = "ALL".equalsIgnoreCase(state) ? null
                : (state == null || state.isBlank() ? "ACTIVE" : state.toUpperCase());
        return paymentPlanUserRepository.findPlans(effectiveState, blankToNull(username), pageable).getContent()
                .stream()
                .map(this::toAssignmentModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countAssignments(String state, String username) {
        String effectiveState = "ALL".equalsIgnoreCase(state) ? null
                : (state == null || state.isBlank() ? "ACTIVE" : state.toUpperCase());
        return paymentPlanUserRepository.countPlans(effectiveState, blankToNull(username));
    }

    @Transactional(readOnly = true)
    public List<PlanUserModel> findActiveAssignments(String username) {
        return paymentPlanUserRepository.findActiveByUserName(username).stream()
                .map(this::toAssignmentModel)
                .toList();
    }

    // ------------------------------------------------------------------ quota

    /**
     * Environments still creatable for this user across all ACTIVE plans.
     */
    @Transactional(readOnly = true)
    public int findRemainingQuota(String username) {
        return paymentPlanUserRepository.findUsableActiveByUserName(username).stream()
                .mapToInt(a -> a.getEnvQuota() - a.getUsedCount())
                .sum();
    }

    /**
     * Consume one environment slot from the oldest usable ACTIVE assignment.
     * Row is locked for update so concurrent generations cannot oversell.
     *
     * @return true when a slot was consumed, false when the user has none left
     */
    @Transactional
    public boolean deductOneQuota(String username) {
        List<PaymentPlanUser> usable = paymentPlanUserRepository.findUsableActiveByUserNameForUpdate(username);
        if (usable.isEmpty()) {
            return false;
        }
        PaymentPlanUser assignment = usable.get(0);
        assignment.setUsedCount(assignment.getUsedCount() + 1);
        paymentPlanUserRepository.save(assignment);
        log.info("Deducted environment quota for [{}] from plan [{}], used={}/{}", username,
                assignment.getPlanUserCode(), assignment.getUsedCount(), assignment.getEnvQuota());
        return true;
    }

    // ----------------------------------------------------------- state changes

    /**
     * CREATE/DEACTIVE -> ACTIVE with the admin chosen start date; the end date
     * is derived from the plan length.
     *
     * @param activeDate yyyy-MM-dd or yyyy-MM-dd HH:mm:ss; today when blank
     */
    @Transactional
    public PlanUserModel activate(String planUserCode, String activeDate) {
        PaymentPlanUser assignment = getEntityByCode(planUserCode);
        Timestamp start = parseActiveDate(activeDate);
        Timestamp end = Timestamp.valueOf(start.toLocalDateTime().plusDays(assignment.getPlan().getPlanDay()));
        assignment.setPlanState("ACTIVE");
        assignment.setActiveDate(start);
        assignment.setEndedDate(end);
        return toAssignmentModel(paymentPlanUserRepository.save(assignment));
    }

    @Transactional
    public PlanUserModel deactivate(String planUserCode) {
        PaymentPlanUser assignment = getEntityByCode(planUserCode);
        assignment.setPlanState("DEACTIVE");
        return toAssignmentModel(paymentPlanUserRepository.save(assignment));
    }

    private Timestamp parseActiveDate(String activeDate) {
        if (activeDate == null || activeDate.isBlank()) {
            return Timestamp.valueOf(LocalDate.now().atStartOfDay());
        }
        String value = activeDate.trim();
        try {
            if (value.length() <= 10) {
                return Timestamp.valueOf(LocalDate.parse(value).atStartOfDay());
            }
            return Timestamp.valueOf(value.replace(" ", "T").replace("T", " ").trim());
        } catch (Exception e) {
            log.warn("Unparseable active date [{}], falling back to today", activeDate);
            return Timestamp.valueOf(LocalDate.now().atStartOfDay());
        }
    }

    private PaymentPlanUser getEntityByCode(String planUserCode) {
        return paymentPlanUserRepository.findByPlanUserCode(planUserCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan assignment not found: " + planUserCode));
    }

    /**
     * Entity lookup for sibling core services (core-to-core only; entities are
     * never handed above the core layer).
     */
    @Transactional(readOnly = true)
    public PaymentPlanUser getAssignmentEntityByCode(String planUserCode) {
        return getEntityByCode(planUserCode);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------- conversions

    public PlanUserModel toAssignmentModel(PaymentPlanUser assignment) {
        PaymentPlan plan = assignment.getPlan();
        return PlanUserModel.builder()
                .planUserCode(assignment.getPlanUserCode())
                .username(assignment.getUser().getUserName())
                .displayName(assignment.getUser().getName())
                .fee(plan.getFee())
                .planDay(plan.getPlanDay())
                .envQuota(assignment.getEnvQuota())
                .usedCount(assignment.getUsedCount())
                .remaining(Math.max(0, assignment.getEnvQuota() - assignment.getUsedCount()))
                .planState(assignment.getPlanState())
                .activeDate(DateUtils.dateToString(assignment.getActiveDate()))
                .endedDate(DateUtils.dateToString(assignment.getEndedDate()))
                .createdAt(DateUtils.dateToString(assignment.getCreatedAt()))
                .build();
    }

    public PaymentPlanModel toPlanModel(PaymentPlan plan) {
        return PaymentPlanModel.builder()
                .planCode(plan.getPlanCode())
                .fee(plan.getFee())
                .planDay(plan.getPlanDay())
                .envCount(plan.getEnvCount())
                .description(plan.getDescription())
                .planState(plan.getPlanState())
                .build();
    }
}
