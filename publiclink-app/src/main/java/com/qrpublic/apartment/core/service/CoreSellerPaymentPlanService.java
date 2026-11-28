package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.entity.SellerPaymentPlan;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.repository.SellerPaymentPlanRepository;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Core layer for seller-defined buyer payment plans (days mapped to a VND
 * amount). Day consumption/refund runs under a pessimistic write lock so
 * concurrent bookings cannot oversell days.
 */
@Slf4j
@Service
public class CoreSellerPaymentPlanService {

    @Autowired
    private SellerPaymentPlanRepository sellerPaymentPlanRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Transactional
    public SellerPlanModel createPlan(String envId, String sellerUsername, String buyerName,
            String buyerUsername, String buyerEmail, int totalDays, BigDecimal amountVnd, String quotaType) {
        SaleEnvironment env = saleEnvironmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));
        if (sellerPaymentPlanRepository.findByEnvIdAndBuyerUsername(envId, buyerUsername).isPresent()) {
            throw new ApplicationException("This buyer already has a plan in this environment.", 409);
        }

        SellerPaymentPlan plan = new SellerPaymentPlan();
        plan.setSellerPlanCode(UUID.randomUUID().toString());
        plan.setSaleEnvironment(env);
        plan.setSellerUsername(sellerUsername);
        plan.setBuyerName(buyerName);
        plan.setBuyerUsername(buyerUsername);
        plan.setBuyerEmail(buyerEmail);
        plan.setTotalDays(totalDays);
        plan.setUsedDays(0);
        plan.setUsedBookings(0);
        plan.setQuotaType(normalizeQuotaType(quotaType));
        plan.setAmountVnd(amountVnd);
        plan.setState("ACTIVE");
        return toModel(sellerPaymentPlanRepository.save(plan));
    }

    /**
     * Seller tops up / edits the plan: quota, amount and unit; the buyer
     * identity stays fixed. The unit may only change while nothing has been
     * consumed, because the stored counters mean different things per unit.
     */
    @Transactional
    public SellerPlanModel updatePlan(String sellerPlanCode, Integer totalDays, BigDecimal amountVnd,
            String buyerEmail, String state, String quotaType) {
        SellerPaymentPlan plan = getByCode(sellerPlanCode);
        if (quotaType != null && !quotaType.isBlank()) {
            String wanted = normalizeQuotaType(quotaType);
            if (!wanted.equals(plan.getQuotaType())) {
                if (plan.getUsedDays() > 0 || plan.getUsedBookings() > 0) {
                    throw new ApplicationException(
                            "The package unit cannot change once bookings have started.", 400);
                }
                plan.setQuotaType(wanted);
            }
        }
        if (totalDays != null) {
            if (totalDays < usedOf(plan)) {
                throw new ApplicationException("Total cannot go below the "
                        + unitName(plan) + " already used.", 400);
            }
            plan.setTotalDays(totalDays);
        }
        if (amountVnd != null) {
            plan.setAmountVnd(amountVnd);
        }
        if (buyerEmail != null) {
            plan.setBuyerEmail(buyerEmail.isBlank() ? null : buyerEmail.trim());
        }
        if (state != null && !state.isBlank()) {
            plan.setState(state.toUpperCase());
        }
        plan.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return toModel(sellerPaymentPlanRepository.save(plan));
    }

    public static String normalizeQuotaType(String quotaType) {
        return SellerPaymentPlan.QUOTA_BOOKINGS.equalsIgnoreCase(quotaType == null ? "" : quotaType.trim())
                ? SellerPaymentPlan.QUOTA_BOOKINGS
                : SellerPaymentPlan.QUOTA_DAYS;
    }

    /** Units consumed so far, counted in the unit this plan uses. */
    private static int usedOf(SellerPaymentPlan plan) {
        return SellerPaymentPlan.QUOTA_BOOKINGS.equals(plan.getQuotaType())
                ? plan.getUsedBookings()
                : plan.getUsedDays();
    }

    private static String unitName(SellerPaymentPlan plan) {
        return SellerPaymentPlan.QUOTA_BOOKINGS.equals(plan.getQuotaType()) ? "bookings" : "days";
    }

    @Transactional(readOnly = true)
    public List<SellerPlanModel> findPlansByEnv(String envId) {
        return sellerPaymentPlanRepository.findByEnvId(envId).stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerPlanModel findByCode(String sellerPlanCode) {
        return toModel(getByCode(sellerPlanCode));
    }

    @Transactional(readOnly = true)
    public SellerPlanModel findByEnvAndBuyer(String envId, String buyerUsername) {
        return sellerPaymentPlanRepository.findByEnvIdAndBuyerUsername(envId, buyerUsername)
                .map(this::toModel)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SellerPlanModel> findActivePlansByBuyer(String buyerUsername) {
        return sellerPaymentPlanRepository.findActiveByBuyerUsername(buyerUsername).stream()
                .map(this::toModel)
                .toList();
    }

    /**
     * Consume plan quota under a row lock. The booking count always grows; a
     * day grows only when {@code newDay} says the buyer had no PLAN booking on
     * that date yet, which is what lets a DAYS package pay for several slots in
     * one day with a single unit. Exhaustion is judged on the unit the plan
     * counts, so a BOOKINGS package runs out per booking instead of per date.
     */
    @Transactional
    public Long consumeQuota(String sellerPlanCode, boolean newDay) {
        SellerPaymentPlan plan = sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate(sellerPlanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Seller plan not found: " + sellerPlanCode));
        if (!"ACTIVE".equals(plan.getState())) {
            throw new ApplicationException("This buyer's payment plan is not active.", 403);
        }
        if (usedOf(plan) >= plan.getTotalDays()) {
            throw new ApplicationException("The buyer's payment plan has no " + unitName(plan) + " left ("
                    + usedOf(plan) + "/" + plan.getTotalDays() + ").", 403);
        }
        if (newDay) {
            plan.setUsedDays(plan.getUsedDays() + 1);
        }
        plan.setUsedBookings(plan.getUsedBookings() + 1);
        plan.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        sellerPaymentPlanRepository.save(plan);
        log.info("Consumed plan quota [{}] unit [{}] for buyer [{}], days={}/{}, bookings={}",
                sellerPlanCode, plan.getQuotaType(), plan.getBuyerUsername(),
                plan.getUsedDays(), plan.getTotalDays(), plan.getUsedBookings());
        return plan.getSellerPlanId();
    }

    /**
     * Return quota for a cancelled PLAN booking. The booking count always
     * drops; a day is only returned when {@code lastOfDay} says no other open
     * PLAN booking remains on that date, otherwise the day is still in use.
     * Never fails the cancel when the plan row is gone (historical booking).
     */
    @Transactional
    public void refundQuota(Long sellerPlanId, boolean lastOfDay) {
        if (sellerPlanId == null) {
            return;
        }
        sellerPaymentPlanRepository.findById(sellerPlanId).ifPresent(plan -> {
            if (plan.getUsedBookings() > 0) {
                plan.setUsedBookings(plan.getUsedBookings() - 1);
            }
            if (lastOfDay && plan.getUsedDays() > 0) {
                plan.setUsedDays(plan.getUsedDays() - 1);
            }
            plan.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            sellerPaymentPlanRepository.save(plan);
            log.info("Refunded plan quota for plan id {}, days={}/{}, bookings={}", sellerPlanId,
                    plan.getUsedDays(), plan.getTotalDays(), plan.getUsedBookings());
        });
    }

    private SellerPaymentPlan getByCode(String sellerPlanCode) {
        return sellerPaymentPlanRepository.findBySellerPlanCode(sellerPlanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Seller plan not found: " + sellerPlanCode));
    }

    public SellerPlanModel toModel(SellerPaymentPlan plan) {
        return SellerPlanModel.builder()
                .sellerPlanCode(plan.getSellerPlanCode())
                .envId(plan.getSaleEnvironment().getEnvId())
                .sellerUsername(plan.getSellerUsername())
                .buyerName(plan.getBuyerName())
                .buyerUsername(plan.getBuyerUsername())
                .buyerEmail(plan.getBuyerEmail())
                .totalDays(plan.getTotalDays())
                .usedDays(plan.getUsedDays())
                .usedBookings(plan.getUsedBookings())
                .quotaType(plan.getQuotaType())
                .remainingDays(Math.max(0, plan.getTotalDays() - usedOf(plan)))
                .amountVnd(plan.getAmountVnd())
                .state(plan.getState())
                .createdAt(DateUtils.dateToString(plan.getCreatedAt()))
                .build();
    }
}
