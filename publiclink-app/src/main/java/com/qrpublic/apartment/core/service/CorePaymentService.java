package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.entity.Bill;
import com.qrpublic.apartment.entity.Payment;
import com.qrpublic.apartment.entity.PaymentPlanUser;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.exception.ValidationException;
import com.qrpublic.apartment.repository.BillRepository;
import com.qrpublic.apartment.repository.PaymentRepository;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core layer for payments and bills. The only layer touching Payment / Bill
 * entities and repositories; everything above works with codes and core models.
 */
@Slf4j
@Service
public class CorePaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CorePaymentPlanService corePaymentPlanService;

    /**
     * Money record of a plan assignment. Starts in CREATE and carries the admin
     * who created it, so the public payment page knows whose transfer account to
     * display.
     */
    @Transactional
    public PaymentModel createPayment(String planUserCode, BigDecimal amount, String adminUsername) {
        Payment payment = new Payment();
        payment.setPaymentCode(UUID.randomUUID().toString());
        payment.setPlanUser(corePaymentPlanService.getAssignmentEntityByCode(planUserCode));
        payment.setAmount(amount);
        payment.setCurrency("VND");
        payment.setStatus("CREATE");
        payment.setCreatedBy(adminUsername);
        return toModel(paymentRepository.save(payment));
    }

    @Transactional
    public void updatePaymentUrl(String paymentCode, String paymentUrl) {
        Payment payment = getEntityByCode(paymentCode);
        payment.setPaymentUrl(paymentUrl);
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel findPaymentByCode(String paymentCode) {
        return toModel(getEntityByCode(paymentCode));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findPaymentsByPlanUserCodes(List<String> planUserCodes) {
        if (planUserCodes == null || planUserCodes.isEmpty()) {
            return List.of();
        }
        return paymentRepository.findByPlanUserCodes(planUserCodes).stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findPaymentsByUsername(String username) {
        return paymentRepository.findByUserName(username).stream()
                .map(this::toModel)
                .toList();
    }

    /**
     * Seller confirmed the transfer: CREATE -> WAITING_CONFIRM, contact info
     * captured for the follow-up result delivery.
     */
    @Transactional
    public PaymentModel confirmPayment(String paymentCode, String email, String phone) {
        Payment payment = getEntityByCode(paymentCode);
        if (!"CREATE".equals(payment.getStatus())) {
            throw new ValidationException("Payment is already processed. Current status: " + payment.getStatus());
        }
        payment.setSellerEmail(email);
        payment.setSellerPhone(phone);
        payment.setStatus("WAITING_CONFIRM");
        payment.setConfirmedAt(Timestamp.from(Instant.now()));
        log.info("Payment [{}] confirmed by seller, waiting admin confirm", paymentCode);
        return toModel(paymentRepository.save(payment));
    }

    /**
     * Admin received the money: -> PAID and one bill row is generated from it.
     * Idempotent: an already PAID payment is returned untouched.
     */
    @Transactional
    public PaymentModel markPaid(String paymentCode, String note, String adminUsername) {
        Payment payment = getEntityByCode(paymentCode);
        if ("PAID".equals(payment.getStatus())) {
            return toModel(payment);
        }
        payment.setStatus("PAID");
        payment.setPaidAt(Timestamp.from(Instant.now()));
        paymentRepository.save(payment);

        Bill bill = new Bill();
        bill.setBillCode(UUID.randomUUID().toString());
        bill.setPayment(payment);
        bill.setBilledAmount(payment.getAmount());
        bill.setNote(note);
        bill.setCreatedBy(adminUsername);
        billRepository.save(bill);
        log.info("Bill created for payment [{}] by admin [{}]", paymentCode, adminUsername);
        return toModel(payment);
    }

    @Transactional
    public PaymentModel rejectPayment(String paymentCode) {
        Payment payment = getEntityByCode(paymentCode);
        payment.setStatus("REJECTED");
        return toModel(paymentRepository.save(payment));
    }

    private Payment getEntityByCode(String paymentCode) {
        return paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentCode));
    }

    public PaymentModel toModel(Payment payment) {
        PaymentPlanUser assignment = payment.getPlanUser();
        return PaymentModel.builder()
                .paymentCode(payment.getPaymentCode())
                .planUserCode(assignment.getPlanUserCode())
                .username(assignment.getUser().getUserName())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentUrl(payment.getPaymentUrl())
                .createdBy(payment.getCreatedBy())
                .sellerEmail(payment.getSellerEmail())
                .sellerPhone(payment.getSellerPhone())
                .confirmedAt(DateUtils.dateToString(payment.getConfirmedAt()))
                .paidAt(DateUtils.dateToString(payment.getPaidAt()))
                .createdAt(DateUtils.dateToString(payment.getCreatedAt()))
                .plan(corePaymentPlanService.toPlanModel(assignment.getPlan()))
                .build();
    }
}
