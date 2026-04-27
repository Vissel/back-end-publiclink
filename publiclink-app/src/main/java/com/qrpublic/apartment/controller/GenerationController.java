package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.request.CreateRequestIdRequest;
import com.qrpublic.apartment.saleenv.response.CreateEnvironmentResponse;
import com.qrpublic.apartment.saleenv.response.CreateRequestIdResponse;
import com.qrpublic.apartment.service.AdminService;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.util.ErrorHandler;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/generator")
public class GenerationController {

    @Autowired
    RequestService requestService;

    @Autowired
    AdminService adminService;


    /**
     * 2026 - create sale environment with public link
     * Exceptions are handled by GlobalExceptionHandler
     */
    @PostMapping("/createSaleEnvironment")
    public Mono<ResponseEntity<CreateEnvironmentResponse>> generatePublicLink(@Valid @RequestBody CreateEnvironmentRequest request) {
        log.info("Received request to create sale environment");
        return adminService.createSaleEnvironment(request)
                .map(response -> {
                    log.info("Sale environment created successfully");
                    return response;
                });
    }

    /**
     * Generate a new request ID
     * Exceptions are handled by GlobalExceptionHandler
     */
    @PostMapping("/generateRequestId")
    public Mono<ResponseEntity<Result<CreateRequestIdResponse>>> generateRequestId(@RequestBody CreateRequestIdRequest request) {
        log.info("Received request to generate request ID");

        return requestService.generateRequestId(request)
                .map(response -> {
                    log.info("Request ID generated successfully");
                    Result<CreateRequestIdResponse> result = ErrorHandler.buildSuccessResult(response);
                    return ResponseEntity.ok(result);
                });
    }
}
