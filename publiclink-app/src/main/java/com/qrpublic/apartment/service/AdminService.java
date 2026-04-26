package com.qrpublic.apartment.service;

import com.qrpublic.apartment.exception.EnvironmentCreationException;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.CreateEnvironmentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AdminService {
    @Autowired
    SaleEnvironmentService saleEnvironmentService;

    /**
     * 2026 - create sale environment with public link
     *
     * @param createEnvironmentRequest
     * @return
     */
    public Mono<ResponseEntity<CreateEnvironmentResponse>> createSaleEnvironment(CreateEnvironmentRequest createEnvironmentRequest) {

        SaleEnvDTO responseDTO = saleEnvironmentService.createSaleEnvironment(createEnvironmentRequest);

        // Build response
        CreateEnvironmentResponse response = new CreateEnvironmentResponse();
        response.setAuthLink(responseDTO.getPublicLink());
        response.setRequestUUID(responseDTO.getRequestId());
        response.setCreatedAt(responseDTO.getCreatedAt());
        response.setUrlString(response.buildUrlString());
        return Mono.just(response)
                .map(ResponseEntity::ok)
                .onErrorMap(throwable ->
                        new EnvironmentCreationException("Failed to create sale environment. Please try again later."));
    }
}
