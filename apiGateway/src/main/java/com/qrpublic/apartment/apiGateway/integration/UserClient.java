package com.qrpublic.apartment.apiGateway.integration;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "UserApplication", url = "http://localhost:8082/api/v1/user")
public interface UserClient {
    @PostMapping("/findByUsername")
    ResponseEntity<FindUserResponse> findUserByUsername(@RequestBody FindUserRequest request);

}
