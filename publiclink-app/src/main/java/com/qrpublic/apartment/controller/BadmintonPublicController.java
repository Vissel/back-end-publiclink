package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.badminton.request.BadmintonBookRequest;
import com.qrpublic.apartment.badminton.request.BadmintonBookingCodeRequest;
import com.qrpublic.apartment.badminton.request.BadmintonGridRequest;
import com.qrpublic.apartment.badminton.request.BadmintonTokenRequest;
import com.qrpublic.apartment.badminton.response.BadmintonGridResponse;
import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.service.BadmintonService;
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

/** Public badminton grid and signed-in buyer booking endpoints. */
@Slf4j
@RestController
@RequestMapping("/api/v1/publish/badminton")
public class BadmintonPublicController {
    @Autowired
    private BadmintonService badmintonService;

    @PostMapping("/grid")
    public Mono<ResponseEntity<BadmintonGridResponse>> grid(
            @RequestBody BadmintonGridRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
        return Mono.fromCallable(() -> badmintonService.grid(request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/book")
    public Mono<ResponseEntity<BadmintonBookingModel>> book(
            @RequestBody BadmintonBookRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
        return Mono.fromCallable(() -> badmintonService.book(request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/payment/confirm")
    public Mono<ResponseEntity<BadmintonBookingModel>> paymentConfirm(
            @RequestBody BadmintonBookingCodeRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
        return Mono.fromCallable(() -> badmintonService.paymentConfirm(request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/cancel")
    public Mono<ResponseEntity<BadmintonBookingModel>> cancel(
            @RequestBody BadmintonBookingCodeRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
        return Mono.fromCallable(() -> badmintonService.cancel(request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/my")
    public Mono<ResponseEntity<List<BadmintonBookingModel>>> myBookings(
            @RequestBody BadmintonTokenRequest request,
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
        return Mono.fromCallable(() -> badmintonService.myBookings(request, caller))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
