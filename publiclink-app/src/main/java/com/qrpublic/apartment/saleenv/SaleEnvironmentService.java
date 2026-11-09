package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.ListEnvironmentResponse;
import com.qrpublic.apartment.template.model.Result;
import reactor.core.publisher.Mono;

public interface SaleEnvironmentService {
    public SaleEnvironment generateSaleEnvironment(Request request);

    /**
     * Create a sale environment for a given request and return the public link and
     * other details.
     *
     * @param request
     * @return
     */
    SaleEnvDTO generateSaleEnvironment(CreateEnvironmentRequest request);

    Mono<Result<ListEnvironmentResponse>> getEnvironments(
            Pagination<ListEnvironmentRequest> listEnvironmentRequestPagination);

    public SaleEnvironment getEnvironmentByPublicLink(String publicLink);

    public String getPublicLinkBy(Request request);

    SaleEnvDTO getSaleEnvironmentByRequestUuid(String requestUuid);

    /**
     * Get detailed environment info including pricing records for a given request
     * UUID.
     */
    Mono<SaleEnvDTO> getEnvironmentDetailByRequestUuid(String requestUuid);

    /**
     * Extend the seller authentication link by generating a new token with extended
     * expiry.
     */
    Mono<SaleEnvDTO> extendAuthLink(String requestUuid);

}
