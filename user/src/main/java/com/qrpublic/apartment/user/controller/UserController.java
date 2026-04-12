package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
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
    UserService userService;

    @PostMapping("/createUser")
    public ResponseEntity<Result<UserCreateResponse>> createUser(@Valid @RequestBody UserRegisterRequest request) {
        Result<UserCreateResponse> result = userService.createUser(request);
        return ResponseEntityConvertor.convert(result);
    }

    @PostMapping("/findByUsername")
    public ResponseEntity<FindUserResponse> findUserByUsername(@RequestBody FindUserRequest request) {
        return convertToFindUserResponse(userService.findByUserName(request.getUserName()));
    }

    private ResponseEntity<FindUserResponse> convertToFindUserResponse(Result<FoundUserResponse> response) {
        if (response.isSuccess()) {
            FoundUserResponse foundUserResponse = response.getData();
            FindUserResponse findUserResponse = new FindUserResponse();
            findUserResponse.setUserName(foundUserResponse.getUserName());
            findUserResponse.setName(foundUserResponse.getName());
            findUserResponse.setEmail(foundUserResponse.getEmail());
            return ResponseEntity.ok(findUserResponse);
        }
        // error message is hide here
        return ResponseEntity.status(response.getErrorCode()).body(null);
    }
}
