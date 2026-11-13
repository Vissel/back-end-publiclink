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
            String buyerUsername, String buyerEmail, int totalDays, BigDecimal amountVnd) {
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
        plan.setAmountVnd(amountVnd);
        plan.setState("ACTIVE");
        return toModel(sellerPaymentPlanRepository.save(plan));
    }

    /**
     * Seller tops up / edits the plan: days and amount only; the buyer
     * identity stays fixed.
     */
    @Transactional
    public SellerPlanModel updatePlan(String sellerPlanCode, Integer totalDays, BigDecimal amountVnd,
            String buyerEmail, String state) {
        SellerPaymentPlan plan = getByCode(sellerPlanCode);
        if (totalDays != null) {
            if (totalDays < plan.getUsedDays()) {
                throw new ApplicationException("Total days cannot go below the days already used.", 400);
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
     * Consume one plan day under a row lock. Returns the plan id for the
     * booking row; refuses when the plan is inactive or has no day left.
     */
    @Transactional
    public Long consumeDay(String sellerPlanCode) {
        SellerPaymentPlan plan = sellerPaymentPlanRepository.findBySellerPlanCodeForUpdate(sellerPlanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Seller plan not found: " + sellerPlanCode));
        if (!"ACTIVE".equals(plan.getState())) {
            throw new ApplicationException("This buyer's payment plan is not active.", 403);
        }
        if (plan.getUsedDays() >= plan.getTotalDays()) {
            throw new ApplicationException("The buyer's payment plan has no day left ("
                    + plan.getUsedDays() + "/" + plan.getTotalDays() + ").", 403);
        }
        plan.setUsedDays(plan.getUsedDays() + 1);
        plan.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        sellerPaymentPlanRepository.save(plan);
        log.info("Consumed plan day [{}] for buyer [{}], used={}/{}", sellerPlanCode,
                plan.getBuyerUsername(), plan.getUsedDays(), plan.getTotalDays());
        return plan.getSellerPlanId();
    }

    /**
     * Refund the day of a cancelled PLAN booking. Never fails the cancel when
     * the plan row is gone (historical booking).
     */
    @Transactional
    public void refundDay(Long sellerPlanId) {
        if (sellerPlanId == null) {
            return;
        }
        sellerPaymentPlanRepository.findById(sellerPlanId).ifPresent(plan -> {
            if (plan.getUsedDays() > 0) {
                plan.setUsedDays(plan.getUsedDays() - 1);
                plan.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
                sellerPaymentPlanRepository.save(plan);
                log.info("Refunded plan day for plan id {}, used={}/{}", sellerPlanId,
                        plan.getUsedDays(), plan.getTotalDays());
            }
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
                .remainingDays(Math.max(0, plan.getTotalDays() - plan.getUsedDays()))
                .amountVnd(plan.getAmountVnd())
                .state(plan.getState())
                .createdAt(DateUtils.dateToString(plan.getCreatedAt()))
                .build();
    }
}
