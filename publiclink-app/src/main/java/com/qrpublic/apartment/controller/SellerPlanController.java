package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.paymentplan.request.CheckPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CheckPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ListPlanUserResponse;
import com.qrpublic.apartment.service.PaymentPlanAdminService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Seller view of their own ACTIVE payment plans and remaining quota. The
 * caller is taken from the gateway-forwarded header, never from the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sellerPlan")
public class SellerPlanController {

    @Autowired
    private PaymentPlanAdminService paymentPlanAdminService;

    @GetMapping("/myPlans")
    public Mono<ResponseEntity<ListPlanUserResponse>> myPlans(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String sellerUsername) {
        return Mono.fromCallable(() -> paymentPlanAdminService.listUserPlans(sellerUsername))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Quota gate for the seller-facing Generate Product Link flow. The seller
     * is taken from the header, so nobody can probe another seller's quota.
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<CheckPlanResponse>> check(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String sellerUsername) {
        CheckPlanRequest request = new CheckPlanRequest();
        request.setUsername(sellerUsername);
        return Mono.fromCallable(() -> paymentPlanAdminService.checkPlan(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
