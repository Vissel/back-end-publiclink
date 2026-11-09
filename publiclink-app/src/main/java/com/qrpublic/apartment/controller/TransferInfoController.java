package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.service.TransferInfoService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.transferinfo.request.CreateTransferInfoRequest;
import com.qrpublic.apartment.transferinfo.request.TransferCodeRequest;
import com.qrpublic.apartment.transferinfo.response.TransferInfoListResponse;
import jakarta.validation.Valid;
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
 * Transfer info (bank / e-wallet) endpoints shared by admin and seller. The
 * owner is always the authenticated caller, so one controller serves both.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transferInfo")
public class TransferInfoController {

    @Autowired
    private TransferInfoService transferInfoService;

    @GetMapping("/list")
    public Mono<ResponseEntity<TransferInfoListResponse>> list(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username,
            @RequestParam(required = false) String status) {
        return Mono.fromCallable(() -> transferInfoService.list(username, status))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/create")
    public Mono<ResponseEntity<TransferInfoListResponse.TransferItem>> create(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username,
            @Valid @RequestBody CreateTransferInfoRequest request) {
        return Mono.fromCallable(() -> transferInfoService.create(username, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/setDefault")
    public Mono<ResponseEntity<Void>> setDefault(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username,
            @RequestBody TransferCodeRequest request) {
        return Mono.fromCallable(() -> transferInfoService.setDefault(username, request.getTransferCode()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }

    @PostMapping("/remove")
    public Mono<ResponseEntity<Void>> remove(
            @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String username,
            @RequestBody TransferCodeRequest request) {
        return Mono.fromCallable(() -> transferInfoService.remove(username, request.getTransferCode()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
    }
}
