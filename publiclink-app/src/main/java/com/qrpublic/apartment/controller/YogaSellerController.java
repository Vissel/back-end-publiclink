package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.service.YogaSellerService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.yoga.request.SellerPlanUpsertRequest;
import com.qrpublic.apartment.yoga.request.YogaBookingToggleRequest;
import com.qrpublic.apartment.yoga.request.YogaClassUpsertRequest;
import com.qrpublic.apartment.yoga.request.YogaRangeRequest;
import com.qrpublic.apartment.yoga.response.YogaResultResponse;
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

import java.util.List;

/**
 * Seller yoga management. Every path requires a JWT at the gateway; the
 * seller identity comes from the forwarded X-User-ID header, never the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/yoga/seller")
public class YogaSellerController {

        @Autowired
        private YogaSellerService yogaSellerService;

        @GetMapping("/environments")
        public Mono<ResponseEntity<List<YogaEnvModel>>> myYogaEnvironments(
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.myYogaEnvironments(seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @GetMapping("/class/list")
        public Mono<ResponseEntity<List<YogaClassModel>>> listClasses(
                        @RequestParam String token,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.listClasses(token, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/class/upsert")
        public Mono<ResponseEntity<YogaResultResponse<YogaClassModel>>> upsertClass(
                        @RequestBody YogaClassUpsertRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.upsertClass(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/class/delete")
        public Mono<ResponseEntity<Void>> deleteClass(
                        @RequestParam String token,
                        @RequestParam String classCode,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.deleteClass(token, classCode, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @GetMapping("/plan/list")
        public Mono<ResponseEntity<List<SellerPlanModel>>> listPlans(
                        @RequestParam String token,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.listPlans(token, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/plan/create")
        public Mono<ResponseEntity<YogaResultResponse<SellerPlanModel>>> createPlan(
                        @RequestBody SellerPlanUpsertRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.createPlan(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/plan/update")
        public Mono<ResponseEntity<YogaResultResponse<SellerPlanModel>>> updatePlan(
                        @RequestBody SellerPlanUpsertRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.updatePlan(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/plan/resend")
        public Mono<ResponseEntity<YogaResultResponse<SellerPlanModel>>> resendPlanLink(
                        @RequestParam String token,
                        @RequestParam String sellerPlanCode,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.resendPlanLink(token, sellerPlanCode, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/booking/list")
        public Mono<ResponseEntity<List<YogaBookingModel>>> listBookings(
                        @RequestBody YogaRangeRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.listBookings(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/booking/toggle")
        public Mono<ResponseEntity<YogaResultResponse<YogaBookingModel>>> toggleBooking(
                        @RequestBody YogaBookingToggleRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> yogaSellerService.toggleBooking(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
