package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PaymentPlanModel;
import com.qrpublic.apartment.core.model.PlanGateModel;
import com.qrpublic.apartment.core.model.PlanUserModel;
import com.qrpublic.apartment.entity.PaymentPlan;
import com.qrpublic.apartment.entity.PaymentPlanUser;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.exception.ValidationException;
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

    @Autowired
    private CoreTimezoneService coreTimezoneService;

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

    /**
     * Append a catalog plan. Deliberately independent of any user: linking it
     * to a seller is a separate, later action (createAssignment). A new row is
     * always inserted so the caller gets the plan it just described instead of
     * being silently pointed at a pre-existing identical one.
     */
    @Transactional
    public PaymentPlanModel createPlan(BigDecimal fee, int planDay, int envCount, String description) {
        PaymentPlan plan = new PaymentPlan();
        plan.setPlanCode(UUID.randomUUID().toString());
        plan.setFee(fee);
        plan.setPlanDay(planDay);
        plan.setEnvCount(envCount);
        plan.setDescription(description);
        plan.setPlanState("ACTIVE");
        log.info("Created payment plan catalog entry fee={} day={} env={}", fee, planDay, envCount);
        return toPlanModel(paymentPlanRepository.save(plan));
    }

    // ------------------------------------------------------------ assignments

    /**
     * Link an existing catalog plan to a user. State starts at CREATE.
     */
    @Transactional
    public PlanUserModel createAssignment(String username, String planCode) {
        PaymentPlan plan = paymentPlanRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan not found: " + planCode));
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

    /**
     * Open an extension order for a running plan. It is an assignment row that
     * grants no environment slot (quota 0) and only carries the days to be
     * added, so it shows up in the "waiting for pay" queue like a plan payment
     * while never becoming a plan window of its own. The stored window is the
     * one projected now; the real end date only moves once the order is paid.
     */
    @Transactional
    public PlanUserModel createExtensionAssignment(String targetPlanUserCode, int days) {
        PaymentPlanUser target = getEntityByCode(targetPlanUserCode);
        if (!"ACTIVE".equals(target.getPlanState())) {
            throw new ValidationException("Only an active plan can be extended. Plan ["
                    + targetPlanUserCode + "] is " + target.getPlanState() + ".");
        }
        Timestamp from = extensionBase(target);
        Timestamp to = Timestamp.valueOf(from.toLocalDateTime().plusDays(days));

        PaymentPlanUser extension = new PaymentPlanUser();
        extension.setPlanUserCode(UUID.randomUUID().toString());
        extension.setUser(target.getUser());
        extension.setPlan(target.getPlan());
        extension.setEnvQuota(0);
        extension.setUsedCount(0);
        extension.setPlanState("CREATE");
        extension.setActiveDate(from);
        extension.setEndedDate(to);
        extension.setExtendDays(days);
        extension.setExtendsPlanUserCode(target.getPlanUserCode());
        log.info("Opened extension order [{}] of {} day(s) for plan [{}]",
                extension.getPlanUserCode(), days, target.getPlanUserCode());
        return toAssignmentModel(paymentPlanUserRepository.save(extension));
    }

    /**
     * The extension is paid: its days are appended to the plan it extends and
     * the order row is closed as EXTENDED, so neither the quota nor the
     * expired-plan message can ever pick the order up as a subscription.
     *
     * @return the new end date of the extended plan
     */
    @Transactional
    public Timestamp applyExtension(String extensionPlanUserCode) {
        PaymentPlanUser extension = getEntityByCode(extensionPlanUserCode);
        if (extension.getExtendDays() == null || extension.getExtendDays() <= 0) {
            throw new ValidationException("Plan [" + extensionPlanUserCode + "] is not an extension order.");
        }
        PaymentPlanUser target = getEntityByCode(extension.getExtendsPlanUserCode());
        // Recomputed at payment time: the seller may have paid long after the
        // link was sent, and days must start from the plan as it is now.
        Timestamp from = extensionBase(target);
        Timestamp to = Timestamp.valueOf(from.toLocalDateTime().plusDays(extension.getExtendDays()));
        target.setEndedDate(to);
        paymentPlanUserRepository.save(target);

        extension.setActiveDate(from);
        extension.setEndedDate(to);
        extension.setPlanState("EXTENDED");
        paymentPlanUserRepository.save(extension);
        log.info("Extended plan [{}] by {} day(s), new end {}",
                target.getPlanUserCode(), extension.getExtendDays(), to);
        return to;
    }

    /**
     * A plan that already ran out is extended from today: adding days to a date
     * in the past would leave the plan unusable. The state is deliberately not
     * flipped when a plan expires, so an expired ACTIVE row is normal here.
     */
    private Timestamp extensionBase(PaymentPlanUser target) {
        Timestamp now = now();
        Timestamp ended = target.getEndedDate();
        return (ended == null || ended.before(now)) ? now : ended;
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

    /**
     * Every assignment of one seller, newest first, whatever its state.
     */
    @Transactional(readOnly = true)
    public List<PlanUserModel> findAllAssignments(String username) {
        return paymentPlanUserRepository.findAllByUserName(username).stream()
                .map(this::toAssignmentModel)
                .toList();
    }

    // ------------------------------------------------------- quota / deadline

    /**
     * Environments still creatable inside the paid window.
     */
    @Transactional(readOnly = true)
    public int findRemainingQuota(String username) {
        return paymentPlanUserRepository.findUsableActiveByUserName(username, now()).stream()
                .mapToInt(a -> a.getEnvQuota() - a.getUsedCount())
                .sum();
    }

    /**
     * Evaluate the seller against their plan window and quota without writing.
     * Distinguishes "no plan", "not started yet", "expired" and "quota used up"
     * so the caller can show the right message.
     */
    @Transactional(readOnly = true)
    public PlanGateModel evaluate(String username) {
        Timestamp now = now();
        List<PaymentPlanUser> active = paymentPlanUserRepository.findActiveByUserName(username);
        if (active.isEmpty()) {
            return PlanGateModel.builder()
                    .allowed(false)
                    .reason(PlanGateModel.REASON_NO_PLAN)
                    .message("You have no active payment plan. Please contact admin via chat or buy a plan.")
                    .remaining(0)
                    .build();
        }

        List<PaymentPlanUser> usable = paymentPlanUserRepository.findUsableActiveByUserName(username, now);
        PlanGateModel gate;
        if (!usable.isEmpty()) {
            PaymentPlanUser chosen = usable.get(0);
            int remaining = usable.stream().mapToInt(a -> a.getEnvQuota() - a.getUsedCount()).sum();
            gate = allowedGate(chosen, remaining);
        } else {
            gate = blockedGate(active, now);
        }
        // The bound for stretching a sale window that already exists, next to
        // the creating plan's own end: a later plan must not stay invisible here
        // or the seller would be refused coverage they already paid for.
        gate.setCoverageEndedDate(DateUtils.dateToString(findPaidCoverageEnd(active, now)));
        return gate;
    }

    /**
     * Latest instant the seller has paid for, quota ignored. Extending a sale
     * window that already exists consumes no new environment slot, so even a
     * seller whose links are all used up must be able to run a sale to the end
     * of their coverage, including the days an extension order added. Plans
     * that already ran out buy no coverage and are skipped; null means the
     * seller is not paid for any future moment.
     */
    @Transactional(readOnly = true)
    public Timestamp findPaidCoverageEnd(String username) {
        return findPaidCoverageEnd(paymentPlanUserRepository.findActiveByUserName(username), now());
    }

    private Timestamp findPaidCoverageEnd(List<PaymentPlanUser> active, Timestamp now) {
        return active.stream()
                .map(PaymentPlanUser::getEndedDate)
                .filter(java.util.Objects::nonNull)
                .filter(ended -> ended.after(now))
                .max(java.util.Comparator.comparing(Timestamp::getTime))
                .orElse(null);
    }

    /**
     * Consume one environment slot from the oldest usable assignment inside the
     * paid window. The row is locked for update so concurrent generations
     * cannot oversell. Returns the gate describing the outcome; when nothing
     * could be consumed the reason explains why.
     */
    @Transactional
    public PlanGateModel consumeOne(String username) {
        Timestamp now = now();
        List<PaymentPlanUser> usable = paymentPlanUserRepository
                .findUsableActiveByUserNameForUpdate(username, now);
        if (usable.isEmpty()) {
            return evaluate(username);
        }

        PaymentPlanUser assignment = usable.get(0);
        assignment.setUsedCount(assignment.getUsedCount() + 1);
        paymentPlanUserRepository.save(assignment);
        log.info("Consumed environment slot for [{}] from plan [{}], used={}/{}, deadline={}", username,
                assignment.getPlanUserCode(), assignment.getUsedCount(), assignment.getEnvQuota(),
                assignment.getEndedDate());

        int remaining = usable.stream()
                .mapToInt(a -> a.getEnvQuota() - a.getUsedCount())
                .sum();
        return allowedGate(assignment, remaining);
    }

    private PlanGateModel allowedGate(PaymentPlanUser assignment, int remaining) {
        return PlanGateModel.builder()
                .allowed(true)
                .reason(PlanGateModel.REASON_OK)
                .remaining(Math.max(0, remaining))
                .activeDate(DateUtils.dateToString(assignment.getActiveDate()))
                .endedDate(DateUtils.dateToString(assignment.getEndedDate()))
                .deadline(assignment.getEndedDate())
                .build();
    }

    private PlanGateModel blockedGate(List<PaymentPlanUser> active, Timestamp now) {
        // Prefer the most recently ended plan so the message quotes a real date
        PaymentPlanUser latestEnded = active.stream()
                .filter(a -> a.getEndedDate() != null && !a.getEndedDate().after(now))
                .max(java.util.Comparator.comparing(PaymentPlanUser::getEndedDate))
                .orElse(null);
        if (latestEnded != null) {
            return PlanGateModel.builder()
                    .allowed(false)
                    .reason(PlanGateModel.REASON_EXPIRED)
                    .message("Your payment plan ended on " + DateUtils.dateToString(latestEnded.getEndedDate())
                            + ". You cannot create more product links. "
                            + "Please contact admin via chat or buy a new plan.")
                    .remaining(0)
                    .activeDate(DateUtils.dateToString(latestEnded.getActiveDate()))
                    .endedDate(DateUtils.dateToString(latestEnded.getEndedDate()))
                    .build();
        }

        PaymentPlanUser notStarted = active.stream()
                .filter(a -> a.getActiveDate() != null && a.getActiveDate().after(now))
                .min(java.util.Comparator.comparing(PaymentPlanUser::getActiveDate))
                .orElse(null);
        if (notStarted != null) {
            return PlanGateModel.builder()
                    .allowed(false)
                    .reason(PlanGateModel.REASON_NOT_STARTED)
                    .message("Your payment plan starts on " + DateUtils.dateToString(notStarted.getActiveDate())
                            + ". Please try again from that date, or contact admin via chat.")
                    .remaining(0)
                    .activeDate(DateUtils.dateToString(notStarted.getActiveDate()))
                    .endedDate(DateUtils.dateToString(notStarted.getEndedDate()))
                    .build();
        }

        return PlanGateModel.builder()
                .allowed(false)
                .reason(PlanGateModel.REASON_QUOTA_EXHAUSTED)
                .message("Your payment plan has no environment slot left. "
                        + "Please contact admin via chat or buy more.")
                .remaining(0)
                .activeDate(DateUtils.dateToString(active.get(0).getActiveDate()))
                .endedDate(DateUtils.dateToString(active.get(0).getEndedDate()))
                .build();
    }

    private Timestamp now() {
        return new Timestamp(coreTimezoneService.getNow().getTime());
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
        if (assignment.getExtendDays() != null) {
            // Approving an order row would turn it into a real plan window and
            // leave the extended plan untouched, so it has to go through the
            // payment confirmation instead.
            throw new ValidationException("Plan [" + planUserCode
                    + "] is an extension order. Confirm its payment instead of approving it.");
        }
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
        Integer extendDays = assignment.getExtendDays();
        // An extension order buys days, not links: the tables have to read the
        // bought days and the price actually due, not the 30 days and full fee
        // of the plan underneath.
        BigDecimal fee = extendDays == null ? plan.getFee()
                : extensionFee(plan.getFee(), plan.getPlanDay(), extendDays);
        int planDay = extendDays == null ? plan.getPlanDay() : extendDays;
        return PlanUserModel.builder()
                .planUserCode(assignment.getPlanUserCode())
                .username(assignment.getUser().getUserName())
                .displayName(assignment.getUser().getName())
                .fee(fee)
                .planDay(planDay)
                .envQuota(assignment.getEnvQuota())
                .usedCount(assignment.getUsedCount())
                .remaining(Math.max(0, assignment.getEnvQuota() - assignment.getUsedCount()))
                .planState(assignment.getPlanState())
                .activeDate(DateUtils.dateToString(assignment.getActiveDate()))
                .endedDate(DateUtils.dateToString(assignment.getEndedDate()))
                .extendDays(extendDays)
                .extendsPlanUserCode(assignment.getExtendsPlanUserCode())
                .createdAt(DateUtils.dateToString(assignment.getCreatedAt()))
                .build();
    }

    /**
     * Price of extra days on a plan: the daily rate, rounded up so a short
     * extension of a cheap plan never collapses to zero. A plan without a
     * length cannot be rate-limited and keeps its full fee.
     */
    public BigDecimal extensionFee(BigDecimal fee, int planDay, int days) {
        BigDecimal safeFee = fee == null ? BigDecimal.ZERO : fee;
        if (planDay <= 0 || safeFee.signum() <= 0) {
            return safeFee;
        }
        return safeFee.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(planDay), 0, java.math.RoundingMode.CEILING);
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
