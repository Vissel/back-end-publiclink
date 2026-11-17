package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.paymentplan.request.ApprovePlanRequest;
import com.qrpublic.apartment.paymentplan.request.CheckPlanRequest;
import com.qrpublic.apartment.paymentplan.request.CreateCatalogPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ExtendPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ListPlanUserRequest;
import com.qrpublic.apartment.paymentplan.request.MarkPaidRequest;
import com.qrpublic.apartment.paymentplan.request.RegenerateLinkRequest;
import com.qrpublic.apartment.paymentplan.request.RejectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CheckPlanResponse;
import com.qrpublic.apartment.paymentplan.response.CreatedPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ExtendPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ListPlanUserResponse;
import com.qrpublic.apartment.paymentplan.response.PlanCatalogResponse;
import com.qrpublic.apartment.paymentplan.response.RegenerateLinkResponse;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.service.PaymentPlanAdminService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Admin payment plan management. Routing to this controller already requires a
 * valid JWT at the gateway; identity comes from the forwarded X-User-ID header.
 */
@Slf4j
@RestController
@RequestMapping("/admin/v1/paymentPlan")
public class PaymentPlanAdminController {

    @Autowired
    private PaymentPlanAdminService paymentPlanAdminService;

    /**
     * Create a payment plan in the catalog. No seller is required: the plan
     * exists on its own and can be linked to one later, from this same dialog
     * or afterwards.
     */
    @PostMapping("/create")
    public Mono<ResponseEntity<CreatedPlanResponse>> create(@RequestBody CreateCatalogPlanRequest request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.createCatalogPlan(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Quota gate in front of the Generate Product Link second screen.
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<CheckPlanResponse>> checkPlan(@RequestBody CheckPlanRequest request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.checkPlan(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @GetMapping("/catalog")
    public Mono<ResponseEntity<PlanCatalogResponse>> catalog() {
        return Mono.fromCallable(() -> paymentPlanAdminService.catalog())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Setup page tables. state=CREATE for the approval table, ACTIVE (default)
     * or ALL for the overview table; username carries the search term.
     */
    @PostMapping("/list")
    public Mono<ResponseEntity<ListPlanUserResponse>> list(
            @RequestBody Pagination<ListPlanUserRequest> request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.listPlanUsers(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * ACTIVE plans of one seller, for the generate-flow plan select.
     */
    @GetMapping("/userPlans")
    public Mono<ResponseEntity<ListPlanUserResponse>> userPlans(@RequestParam String username) {
        return Mono.fromCallable(() -> paymentPlanAdminService.listUserPlans(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/approve")
    public Mono<ResponseEntity<Integer>> approve(@RequestBody ApprovePlanRequest request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.approve(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/reject")
    public Mono<ResponseEntity<Integer>> reject(@RequestBody RejectPlanRequest request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.reject(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Money received: the selected payments go to PAID and their plans start
     * on the given activation date, which moves them out of the "Waiting for
     * pay" table and into the active plan list.
     */
    @PostMapping("/payment/markPaid")
    public Mono<ResponseEntity<Integer>> markPaid(
            @RequestBody MarkPaidRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "admin") String adminUsername) {
        return Mono.fromCallable(() -> paymentPlanAdminService.markPaid(request, adminUsername))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Re-issues the payment URL of the selected plans. Used by the "Waiting for
     * pay" table when a seller lost the link or the previous one expired.
     */
    @PostMapping("/payment/regenerateLink")
    public Mono<ResponseEntity<RegenerateLinkResponse>> regeneratePaymentLink(
            @RequestBody RegenerateLinkRequest request) {
        return Mono.fromCallable(() -> paymentPlanAdminService.regeneratePaymentLink(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    /**
     * Sells extra days on a running plan: creates an extension order in the
     * "Waiting for pay" queue together with its own payment link, priced at the
     * plan's daily rate. The plan's end date only moves when that order is
     * marked paid.
     */
    @PostMapping("/extend")
    public Mono<ResponseEntity<ExtendPlanResponse>> extendPlan(
            @RequestBody ExtendPlanRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "admin") String adminUsername) {
        return Mono.fromCallable(() -> paymentPlanAdminService.extendPlan(request, adminUsername))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
