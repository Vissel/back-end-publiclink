package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.service.request.CreateUserAuthRequest;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.request.UserUpdateRequest;
import com.qrpublic.apartment.user.service.response.CreateUserAuthResponse;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
import com.qrpublic.apartment.user.service.response.UserUpdateResponse;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<Result<FoundUserResponse>> findByUserName(String userName);

    Mono<Result<UserCreateResponse>> createUser(UserCreateRequest userCreateRequest);

    Mono<Result<UserUpdateResponse>> updateUser(UserUpdateRequest userUpdateRequest);

    Mono<Result<UserDeleteResponse>> deleteUserByUsername(UserDeleteRequest userRemoveRequest);

    Mono<Result<CreateUserAuthResponse>> createUserAuth(CreateUserAuthRequest createUserAuthRequest);

}
