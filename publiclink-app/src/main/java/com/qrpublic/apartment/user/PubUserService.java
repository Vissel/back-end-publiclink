package com.qrpublic.apartment.user;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.request.SellerRegisterRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import com.qrpublic.apartment.user.response.ListUserResponse;
import com.qrpublic.apartment.user.response.SellerRegisterResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface PubUserService {
    Result<ListUserResponse> listUser(Pagination<ListUserRequest> request);

    Mono<ResponseEntity<SellerRegisterResponse>> registerNewSeller(SellerRegisterRequest request);

    Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest);
}
