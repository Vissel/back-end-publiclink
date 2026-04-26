package com.qrpublic.apartment.user;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import reactor.core.publisher.Mono;

public interface PubUserService {
    Mono<ListUserResponse> listUser(Pagination<ListUserRequest> request);
}
