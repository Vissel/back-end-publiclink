package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.badminton.request.BadmintonBookingCodeRequest;
import com.qrpublic.apartment.badminton.request.BadmintonCourtDeleteRequest;
import com.qrpublic.apartment.badminton.request.BadmintonCourtSaveRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerBookingsRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerCourtsRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerRejectRequest;
import com.qrpublic.apartment.badminton.response.BadmintonCourtResponse;
import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.service.BadmintonSellerService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
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

import java.util.List;

/**
 * Seller badminton endpoints; the gateway-authenticated header supplies
 * identity.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/badminton/seller")
public class BadmintonSellerController {
        @Autowired
        private BadmintonSellerService badmintonSellerService;

        @PostMapping("/courts")
        public Mono<ResponseEntity<List<BadmintonCourtResponse>>> courts(
                        @RequestBody BadmintonSellerCourtsRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.courts(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/courts/save")
        public Mono<ResponseEntity<BadmintonCourtResponse>> saveCourt(
                        @RequestBody BadmintonCourtSaveRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.saveCourt(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/courts/delete")
        public Mono<ResponseEntity<Void>> deleteCourt(
                        @RequestBody BadmintonCourtDeleteRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.deleteCourt(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/bookings")
        public Mono<ResponseEntity<List<BadmintonBookingModel>>> bookings(
                        @RequestBody BadmintonSellerBookingsRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.bookings(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/bookings/confirm")
        public Mono<ResponseEntity<BadmintonBookingModel>> confirm(
                        @RequestBody BadmintonBookingCodeRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.confirm(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/bookings/reject")
        public Mono<ResponseEntity<BadmintonBookingModel>> reject(
                        @RequestBody BadmintonSellerRejectRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String seller) {
                return Mono.fromCallable(() -> badmintonSellerService.reject(request, seller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
