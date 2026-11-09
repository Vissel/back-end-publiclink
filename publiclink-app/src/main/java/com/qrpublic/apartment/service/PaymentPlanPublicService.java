package com.qrpublic.apartment.service;

import com.qrpublic.apartment.domain.paymentplan.PaymentDomainService;
import com.qrpublic.apartment.paymentplan.request.PaymentConfirmRequest;
import com.qrpublic.apartment.paymentplan.response.PaymentConfirmResponse;
import com.qrpublic.apartment.paymentplan.response.PaymentInfoResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Public payment page operations (no authorization). The token in the payment
 * URL is the only credential.
 */
@Slf4j
@Service
public class PaymentPlanPublicService {

    @Autowired
    private PaymentDomainService paymentDomainService;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    public Result<PaymentInfoResponse> paymentInfo(String token) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, PaymentInfoResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(token, "Payment token is required.");
            }

            @Override
            public PaymentInfoResponse process() {
                return paymentDomainService.paymentInfo(token.trim());
            }
        });
    }

    public Result<PaymentConfirmResponse> confirmPayment(PaymentConfirmRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<PaymentConfirmRequest, PaymentConfirmResponse>() {
            @Override
            public PaymentConfirmRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(PaymentConfirmRequest req) {
                Assert.hasText(req.getToken(), "Payment token is required.");
            }

            @Override
            public PaymentConfirmResponse process() {
                return paymentDomainService.confirmPayment(request.getToken().trim(),
                        request.getEmail(), request.getPhone());
            }
        });
    }
}
