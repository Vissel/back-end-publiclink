package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.account.request.ChangePasswordRequest;
import com.qrpublic.apartment.account.request.UpdateProfileRequest;
import com.qrpublic.apartment.account.response.BuyerPackageResponse;
import com.qrpublic.apartment.account.response.SelfProfileResponse;
import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.service.SelfAccountService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * The signed-in account's own page (/self), open to sellers and buyers alike.
 * The identity is the gateway-forwarded X-User-ID, which the gateway only ever
 * attaches from a validated JWT, so every path here is scoped to the caller and
 * takes no username in the body.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/account")
public class SelfAccountController {

        @Autowired
        private SelfAccountService selfAccountService;

        @GetMapping("/self")
        public Mono<ResponseEntity<SelfProfileResponse>> self(
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> selfAccountService.profile(caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        @PostMapping("/profile")
        public Mono<ResponseEntity<SelfProfileResponse>> updateProfile(
                        @RequestBody UpdateProfileRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> selfAccountService.updateProfile(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Change the caller's own password. Both values are already RSA-encrypted
         * by the browser; the identity is the JWT header, so the call can only ever
         * act on the account making it.
         */
        @PostMapping("/password")
        public Mono<ResponseEntity<Void>> changePassword(
                        @RequestBody ChangePasswordRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> selfAccountService.changePassword(request, caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /** Packages a seller issued to this buyer; empty for a seller account. */
        @GetMapping("/myPackages")
        public Mono<ResponseEntity<List<BuyerPackageResponse>>> myPackages(
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "") String caller) {
                return Mono.fromCallable(() -> selfAccountService.myPackages(caller))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
