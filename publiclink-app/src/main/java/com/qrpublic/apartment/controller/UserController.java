package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.response.ResponseEntityConvertor;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.user.impl.ExternalUserServiceImpl;
import com.qrpublic.apartment.user.interfaces.request.UserCreateRequest;
import com.qrpublic.apartment.user.interfaces.response.UserCreateResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    @Autowired
    ExternalUserServiceImpl userService;

    @PostMapping("/createUser")
    public ResponseEntity<Result<UserCreateResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        Result<UserCreateResponse> result = userService.createUser(request);
        return ResponseEntityConvertor.convert(result);
    }
}
