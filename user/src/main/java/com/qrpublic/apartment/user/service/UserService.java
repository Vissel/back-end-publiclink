package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;

public interface UserService {

    Result<FoundUserResponse> findByUserName(String userName);

    Result<UserCreateResponse> createUser(UserCreateRequest userCreateRequest);

    Result<UserDeleteResponse> deleteUserByUsername(UserDeleteRequest userRemoveRequest);
}
