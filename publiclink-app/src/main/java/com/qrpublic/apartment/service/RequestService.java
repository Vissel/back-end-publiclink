package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import com.qrpublic.apartment.saleenv.request.CreatedRequestIdRequest;
import com.qrpublic.apartment.saleenv.response.CreateRequestIdResponse;
import reactor.core.publisher.Mono;

public interface RequestService {
    public RequestDTO createTempRequestDTO(SellerDTO seller);

    public String generatePublicLink(SellerDTO requestDTO);

    public String generateSellerAuthLink(User seller, long reqId);

    public Request saveRequest(Request request);

    public Request saveAuthenticatedRequest(long requestId);

    Mono<CreateRequestIdResponse> generateRequestId(CreatedRequestIdRequest request);

}
