package com.qrpublic.apartment.domain.paymentplan;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.PaymentModel;
import com.qrpublic.apartment.core.model.TransferInfoModel;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.core.service.CoreTransferInfoService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.paymentplan.response.PaymentConfirmResponse;
import com.qrpublic.apartment.paymentplan.response.PaymentInfoResponse;
import com.qrpublic.apartment.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Payment page business logic (public, no authorization): assembles what the
 * seller sees on the payment URL and records the confirmation, then notifies
 * admins so they can approve the plan.
 */
@Slf4j
@Component
public class PaymentDomainService {

    private static final String RESULT_GUIDANCE =
            "We received your confirmation. You will get the result within a few minutes "
                    + "via app chat, or the email / phone number you provided.";

    @Autowired
    private LinkService linkService;

    @Autowired
    private CorePaymentService corePaymentService;

    @Autowired
    private CoreTransferInfoService coreTransferInfoService;

    @Autowired
    private CoreNotificationService coreNotificationService;

    /**
     * Resolve the payment behind a payment URL token and build the page data.
     */
    @Transactional(readOnly = true)
    public PaymentInfoResponse paymentInfo(String token) {
        PaymentModel payment = resolvePayment(token);
        return toInfoResponse(payment);
    }

    /**
     * Seller confirms the bank transfer. The admin who created the payment is
     * notified so they can verify the money and activate the plan.
     */
    @Transactional
    public PaymentConfirmResponse confirmPayment(String token, String email, String phone) {
        PaymentModel payment = resolvePayment(token);
        PaymentModel updated = corePaymentService.confirmPayment(payment.getPaymentCode(), email, phone);

        String message = "Seller [" + payment.getUsername() + "] confirmed payment of "
                + updated.getAmount() + " " + updated.getCurrency()
                + ". Contact: " + (email == null || email.isBlank() ? "n/a" : email)
                + " / " + (phone == null || phone.isBlank() ? "n/a" : phone)
                + ". Please verify and activate the plan.";
        // Recipients are addressed by username, and admins have no row in
        // publiclink-db.user, so the creating admin is the notification target.
        coreNotificationService.notifyUsernames(List.of(payment.getCreatedBy()),
                "Payment confirmation received", message, "PAYMENT", payment.getUsername());

        PaymentConfirmResponse response = new PaymentConfirmResponse();
        response.setStatus(updated.getStatus());
        response.setMessage(RESULT_GUIDANCE);
        return response;
    }

    private PaymentModel resolvePayment(String token) {
        if (token == null || token.isBlank()) {
            throw new ApplicationException("Payment token is required.", 400);
        }
        if (!linkService.validateLink(token)) {
            throw new ApplicationException("This payment link is invalid or expired. Please contact admin.", 400);
        }
        Object claim = linkService.extractClaimByKey(token, LinkConstant.PARAM_PAYMENT_CODE);
        if (claim == null) {
            throw new ApplicationException("This payment link is invalid. Please contact admin.", 400);
        }
        return corePaymentService.findPaymentByCode(String.valueOf(claim));
    }

    private PaymentInfoResponse toInfoResponse(PaymentModel payment) {
        PaymentInfoResponse response = new PaymentInfoResponse();
        response.setPaymentCode(payment.getPaymentCode());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());

        if (payment.getPlan() != null) {
            response.setFee(payment.getPlan().getFee());
            response.setPlanDay(payment.getPlan().getPlanDay());
            response.setEnvCount(payment.getPlan().getEnvCount());
            response.setDescription(payment.getPlan().getDescription());
            LocalDate today = LocalDate.now();
            response.setEstimatedActiveDate(today.toString());
            response.setEstimatedEndDate(today.plusDays(payment.getPlan().getPlanDay()).toString());
        }

        response.setTransferInfos(transferItemsOf(payment.getCreatedBy()));
        return response;
    }

    private List<PaymentInfoResponse.TransferItem> transferItemsOf(String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) {
            return List.of();
        }
        return coreTransferInfoService.findActiveByOwner(adminUsername).stream()
                .map(coreTransferInfoService::toModel)
                .filter(Objects::nonNull)
                .map(this::toTransferItem)
                .toList();
    }

    private PaymentInfoResponse.TransferItem toTransferItem(TransferInfoModel model) {
        PaymentInfoResponse.TransferItem item = new PaymentInfoResponse.TransferItem();
        item.setTransferIssuer(model.getTransferIssuer());
        item.setIssuerFullName(model.getIssuerFullName());
        item.setType(model.getType());
        item.setTransferNumber(model.getTransferNumber());
        item.setAccountName(model.getAccountName());
        item.setQrImage(model.getQrImage());
        item.setDefaultMessage(model.getDefaultMessage());
        item.setDefault(model.isDefault());
        return item;
    }
}
