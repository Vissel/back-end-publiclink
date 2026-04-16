package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;

public interface UserService {

    Result<FoundUserResponse> findByUserName(String userName);

    Result<UserCreateResponse> createUser(UserRegisterRequest userCreateRequest);
}
