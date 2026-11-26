package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.paymentplan.request.ChangePlanPurposeRequest;
import com.qrpublic.apartment.paymentplan.request.CheckPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ConvertPlanRequest;
import com.qrpublic.apartment.paymentplan.request.ExtendPlanRequest;
import com.qrpublic.apartment.paymentplan.request.SelectPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CheckPlanResponse;
import com.qrpublic.apartment.paymentplan.response.ListPlanUserResponse;
import com.qrpublic.apartment.paymentplan.response.MorePlanStateResponse;
import com.qrpublic.apartment.paymentplan.response.SelfServiceStateResponse;
import com.qrpublic.apartment.service.PaymentPlanAdminService;
import com.qrpublic.apartment.service.SelfServicePlanService;
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
 * Seller view of their own ACTIVE payment plans and remaining quota. The
 * caller is taken from the gateway-forwarded header, never from the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sellerPlan")
public class SellerPlanController {

        @Autowired
        private PaymentPlanAdminService paymentPlanAdminService;

        @Autowired
        private SelfServicePlanService selfServicePlanService;

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
         *
         * @param productType optional; when given the answer is limited to the plan
         *                    group that kind of link has to be paid for by. Taken
         *                    as a query parameter rather than a body because every
         *                    existing caller posts this endpoint with no body at all
         *                    and must keep working unchanged.
         */
        @PostMapping("/check")
        public Mono<ResponseEntity<CheckPlanResponse>> check(
                        @RequestParam(value = "productType", required = false) String productType,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String sellerUsername) {
                CheckPlanRequest request = new CheckPlanRequest();
                request.setUsername(sellerUsername);
                request.setProductType(productType);
                return Mono.fromCallable(() -> paymentPlanAdminService.checkPlan(request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Self-service plan purchase: the seller buys the selected catalog plan and
         * immediately gets the transfer instructions. An order that is already open
         * is handed back instead of a second one being created.
         */
        @PostMapping("/select")
        public Mono<ResponseEntity<SelfServiceStateResponse>> selectPlan(
                        @RequestBody SelectPlanRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String sellerUsername) {
                return Mono.fromCallable(() -> selfServicePlanService.selectPlan(sellerUsername, request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Onboarding state of the seller, polled by the plan screen while an admin
         * verifies the transfer.
         */
        @GetMapping("/current")
        public Mono<ResponseEntity<SelfServiceStateResponse>> current(
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String sellerUsername) {
                return Mono.fromCallable(() -> selfServicePlanService.currentState(sellerUsername))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * "More plan" screen: what the account already pays for plus the order it
         * still has to transfer. Sellers and buyers alike; admins have no plan
         * account to buy with and are refused by the service.
         */
        @GetMapping("/moreState")
        public Mono<ResponseEntity<MorePlanStateResponse>> moreState(
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username) {
                return Mono.fromCallable(() -> selfServicePlanService.moreState(username))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Buy a further plan on top of the running one. An order that is still
         * unpaid is handed back instead of a second one being opened.
         */
        @PostMapping("/buyMore")
        public Mono<ResponseEntity<MorePlanStateResponse>> buyMore(
                        @RequestBody SelectPlanRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username) {
                return Mono.fromCallable(() -> selfServicePlanService.buyMore(username, request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Buy extra days on one of the caller's own ACTIVE plans. The extension
         * order lands in the admin "Waiting for pay" queue like any other payment.
         */
        @PostMapping("/extendMore")
        public Mono<ResponseEntity<MorePlanStateResponse>> extendMore(
                        @RequestBody ExtendPlanRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username) {
                return Mono.fromCallable(() -> selfServicePlanService.extendMore(username, request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Move a running plan onto a dearer plan of the same purpose group for
         * only the difference. Nothing is charged when the new plan costs the
         * same or less; the conversion then applies straight away.
         */
        @PostMapping("/convert")
        public Mono<ResponseEntity<MorePlanStateResponse>> convertPlan(
                        @RequestBody ConvertPlanRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username) {
                return Mono.fromCallable(() -> selfServicePlanService.convertPlan(username, request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Change which plan group this account is offered, for someone who
         * answered the registration question with the wrong business.
         */
        @PostMapping("/purpose")
        public Mono<ResponseEntity<SelfServiceStateResponse>> changePurpose(
                        @RequestBody ChangePlanPurposeRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username) {
                return Mono.fromCallable(() -> selfServicePlanService.changePurpose(username, request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
