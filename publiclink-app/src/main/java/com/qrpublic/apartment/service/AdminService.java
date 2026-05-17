package com.qrpublic.apartment.service;

import com.qrpublic.apartment.exception.EnvironmentCreationException;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.CreateEnvironmentResponse;
import com.qrpublic.apartment.saleenv.response.ListEnvironmentResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@PreAuthorize("hasRole('Admin')")
public class AdminService {
    @Autowired
    SaleEnvironmentService saleEnvironmentService;

    @Autowired
    PubUserService pubUserService;

    /**
     * 2026 - create sale environment with public link
     *
     * @param createEnvironmentRequest
     * @return
     */
    public Mono<ResponseEntity<CreateEnvironmentResponse>> createSaleEnvironment(CreateEnvironmentRequest createEnvironmentRequest) {
        return Mono.fromCallable(() -> saleEnvironmentService.createSaleEnvironment(createEnvironmentRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .map(responseDTO -> {
                    CreateEnvironmentResponse response = new CreateEnvironmentResponse();
                    response.setAuthLink(responseDTO.getSellerAuthLink());
                    response.setRequestUUID(responseDTO.getRequestUUID());
                    response.setCreatedAt(responseDTO.getCreatedAt());
                    response.setExpired(responseDTO.getSellerAuthLinkExpire().toString());
                    response.setUrlString(response.buildUrlString());
                    return ResponseEntity.ok(response);
                })
                .onErrorMap(throwable ->
                        new EnvironmentCreationException("Failed to create sale environment. Please try again later."));
    }

    public Mono<ListUserResponse> listInnerUsers(Pagination<ListUserRequest> listUserRequestPagination) {
        return Mono.fromCallable(() -> pubUserService.listUser(listUserRequestPagination))
                .flatMap(result -> returnResult(result, result.getErrorMessage()));
    }

    public Mono<ListEnvironmentResponse> getSaleEnvironment(Pagination<ListEnvironmentRequest> listEnvironmentRequestPagination) {
        return saleEnvironmentService.getEnvironments(listEnvironmentRequestPagination)
                .flatMap(result -> returnResult(result, "Can not get list environment"));
    }

    private <R> Mono<R> returnResult(Result<R> result, String errorMessage) {
        if (result.isSuccess()) {
            return Mono.just(result.getData());
        } else {
            return Mono.error(new RuntimeException(errorMessage));
        }
    }
}
