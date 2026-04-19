package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
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
    public ResponseEntity<Result<UserRegisterResponse>> createUser(@Valid @RequestBody UserRegisterRequest request) {
        Result<UserCreateResponse> result = userService.createUser(request);
        return convertToUserRegisterResponse(result);
    }

    @PostMapping("/findByUsername")
    public ResponseEntity<FindUserResponse> findUserByUsername(@RequestBody FindUserRequest request) {
        return convertToFindUserResponse(userService.findByUserName(request.getUserName()));
    }

    private ResponseEntity<Result<UserRegisterResponse>> convertToUserRegisterResponse(Result<UserCreateResponse> result) {
        UserRegisterResponse userRegisterResponse = new UserRegisterResponse();
        if (result.isSuccess()) {
            UserCreateResponse userCreateResponse = result.getData();
            userRegisterResponse.setUserName(userCreateResponse.getUserName());
            userRegisterResponse.setLink(userCreateResponse.getLink());
            userRegisterResponse.setRole(userCreateResponse.getRole());
            userRegisterResponse.setPassword(userCreateResponse.getPassword());
            userRegisterResponse.setRole(userCreateResponse.getRole());
            userRegisterResponse.setMessage("User created successfully");
            return ResponseEntity.ok(Result.success(userRegisterResponse));
        }
        // error message is hide here
        userRegisterResponse.setMessage("User created failed");
        return ResponseEntity.status(result.getErrorCode()).body(Result.error(result.getErrorCode(), null));
    }

    private ResponseEntity<FindUserResponse> convertToFindUserResponse(Result<FoundUserResponse> response) {
        if (response.isSuccess()) {
            FoundUserResponse data = response.getData();
            FindUserResponse findUserResponse = new FindUserResponse();
            findUserResponse.setUserName(data.getUserName());
            findUserResponse.setEncodedPassword(data.getEncodedPassword());
            findUserResponse.setName(data.getName());
            findUserResponse.setEmail(data.getEmail());
            findUserResponse.setRole(data.getRole());
            return ResponseEntity.ok(findUserResponse);
        }
        // error message is hide here
        return ResponseEntity.status(response.getErrorCode()).body(null);
    }
}
