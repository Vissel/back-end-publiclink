package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.adapter.user.response.UserRemoveResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "UserApplication", url = "http://localhost:8082/api/v1/user")
public interface OperatedUserClient {
    @PostMapping("/findByUsername")
    ResponseEntity<FindUserResponse> findUserByUsername(@RequestBody FindUserRequest request);

    @PostMapping("/createUser")
    ResponseEntity<Result<UserRegisterResponse>> createUser(@Valid @RequestBody UserRegisterRequest request);

    @PostMapping("/deleteUser")
    ResponseEntity<Result<UserRemoveResponse>> deleteUser(@RequestBody UserRemoveRequest request);
}
