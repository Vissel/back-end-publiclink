package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.adapter.user.response.UserRemoveResponse;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
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
        UserCreateRequest userCreateRequest = toUserCreateRequest(request);
        Result<UserCreateResponse> result = userService.createUser(userCreateRequest);
        return convertToUserRegisterResponse(result);
    }

    @PostMapping("/findByUsername")
    public ResponseEntity<FindUserResponse> findUserByUsername(@RequestBody FindUserRequest request) {
        return convertToFindUserResponse(userService.findByUserName(request.getUserName()));
    }

    @PostMapping("/deleteUser")
    public ResponseEntity<Result<UserRemoveResponse>> deleteUser(@RequestBody UserRemoveRequest request) {
        UserDeleteRequest userDeleteRequest = toUserDeleteRequest(request);
        return convertToUserDeleteResponse(userService.deleteUserByUsername(userDeleteRequest));
    }

    private ResponseEntity<Result<UserRemoveResponse>> convertToUserDeleteResponse(Result<UserDeleteResponse> userDeleteResponseResult) {
        UserRemoveResponse userRemoveResponse = new UserRemoveResponse();
        if (userDeleteResponseResult.isSuccess()) {
            UserDeleteResponse userDeleteResponse = userDeleteResponseResult.getData();
            userRemoveResponse.setUserId(userDeleteResponse.getUserId());
            userRemoveResponse.setDeleted(userDeleteResponse.getDeleted());
            userRemoveResponse.setMessage("User deleted successfully");
            return ResponseEntity.ok(Result.success(userRemoveResponse));
        }
        // error message is hide here
        userRemoveResponse.setMessage("User deletion failed");
        return ResponseEntity.status(userDeleteResponseResult.getErrorCode()).body(Result.error(userDeleteResponseResult.getErrorCode(), null));
    }

    private UserCreateRequest toUserCreateRequest(@Valid UserRegisterRequest request) {
        UserCreateRequest userCreateRequest = new UserCreateRequest();
        userCreateRequest.setUserName(request.getUserName());
        userCreateRequest.setEncryptedPassword(request.getEncryptedPassword());
        userCreateRequest.setFullName(request.getFullName());
        userCreateRequest.setLink(request.getProfileLink() != null ? request.getProfileLink().toString() : null);
        userCreateRequest.setRole(request.getRole());
        return userCreateRequest;
    }

    private UserDeleteRequest toUserDeleteRequest(UserRemoveRequest request) {
        UserDeleteRequest userDeleteRequest = new UserDeleteRequest();
        userDeleteRequest.setUserId(request.getUserId());
        return userDeleteRequest;
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
