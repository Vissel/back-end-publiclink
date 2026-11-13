package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.service.YogaService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.yoga.request.BuyerSetupRequest;
import com.qrpublic.apartment.yoga.request.YogaBookRequest;
import com.qrpublic.apartment.yoga.request.YogaCalendarRequest;
import com.qrpublic.apartment.yoga.request.YogaCancelRequest;
import com.qrpublic.apartment.yoga.request.YogaRangeRequest;
import com.qrpublic.apartment.yoga.response.BuyerSetupInfoResponse;
import com.qrpublic.apartment.yoga.response.YogaOccurrencesResponse;
import com.qrpublic.apartment.yoga.response.YogaResultResponse;
import com.qrpublic.apartment.core.model.YogaBookingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Public yoga calendar + booking endpoints and the buyer setup page. These
 * live under /api/v1/publish/** so the gateway permit-all rule covers them;
 * when a buyer holds a JWT, the gateway still forwards X-User-ID and the plan
 * identity is applied server-side.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/publish/yoga")
public class YogaPublicController {

        @Autowired
        private YogaService yogaService;

        @PostMapping("/calendar")
        public Mono<ResponseEntity<YogaOccurrencesResponse>> calendar(
                        @RequestBody YogaCalendarRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> yogaService.calendar(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/classes")
        public Mono<ResponseEntity<YogaOccurrencesResponse>> classes(
                        @RequestBody YogaRangeRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> yogaService.classes(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/book")
        public Mono<ResponseEntity<YogaResultResponse<YogaBookingModel>>> book(
                        @RequestBody YogaBookRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> yogaService.book(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/cancel")
        public Mono<ResponseEntity<YogaResultResponse<YogaBookingModel>>> cancel(
                        @RequestBody YogaCancelRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> yogaService.cancel(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/setup/info")
        public Mono<ResponseEntity<BuyerSetupInfoResponse>> setupInfo(@RequestBody BuyerSetupRequest request) {
                return Mono.fromCallable(() -> yogaService.setupInfo(request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/setup/complete")
        public Mono<ResponseEntity<Void>> setupComplete(@RequestBody BuyerSetupRequest request) {
                return Mono.fromCallable(() -> yogaService.setupComplete(request))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
