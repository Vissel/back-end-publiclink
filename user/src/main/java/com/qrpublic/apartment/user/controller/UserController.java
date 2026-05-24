package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.request.UserUserAuthRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.adapter.user.response.UserRemoveResponse;
import com.qrpublic.apartment.adapter.user.response.UserUserAuthResponse;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.request.CreateUserAuthRequest;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.request.UserUpdateRequest;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
import com.qrpublic.apartment.user.service.response.UserUpdateResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    UserService userService;

    @PostMapping("/createUser")
    public Mono<ResponseEntity<Result<UserRegisterResponse>>> createUser(@Valid @RequestBody UserRegisterRequest request) {
        return userService.createUser(toUserCreateRequest(request))
                .map(result -> {
                    if (result.isSuccess()) {
                        UserRegisterResponse resp = toUserRegisterResponse(result.getData());
                        resp.setMessage("User created successfully");
                        return ResponseEntity.ok(Result.success(resp));
                    }
                    return ResponseEntity.status(result.getErrorCode()).<Result<UserRegisterResponse>>body(Result.error(result.getErrorCode(), null));
                });
    }

    @PostMapping("/findByUsername")
    public Mono<ResponseEntity<FindUserResponse>> findUserByUsername(@RequestBody FindUserRequest request) {
        return userService.findByUserName(request.getUserName())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(toFindUserResponse(result.getData()));
                    }
                    return ResponseEntity.status(result.getErrorCode()).<FindUserResponse>body(null);
                });
    }

    @PostMapping("/deleteUser")
    public Mono<ResponseEntity<Result<UserRemoveResponse>>> deleteUser(@RequestBody UserRemoveRequest request) {
        return userService.deleteUserByUsername(toUserDeleteRequest(request))
                .map(result -> {
                    if (result.isSuccess()) {
                        UserRemoveResponse resp = toUserRemoveResponse(result.getData());
                        return ResponseEntity.ok(Result.success(resp));
                    }
                    return ResponseEntity.status(result.getErrorCode()).<Result<UserRemoveResponse>>body(Result.error(result.getErrorCode(), null));
                });
    }

    @PostMapping("/createUserAndUserAuth")
    public Mono<Result<UserUserAuthResponse>> createUserAndUserAuth(@Valid @RequestBody UserUserAuthRequest userUserAuthRequest) {
        CreateUserAuthRequest createUserAuthRequest = convertToCreateUserAuthRequest(userUserAuthRequest);
        return userService.createUserAuth(createUserAuthRequest)
                .filter(result -> result.isSuccess())
                .map(
                        createUserAuthResponse -> {
                            UserUserAuthResponse response = new UserUserAuthResponse();
                            response.setSuccess(createUserAuthResponse.isSuccess());
                            response.setErrorMessage(createUserAuthResponse.getErrorMessage());
                            response.setErrorCode(createUserAuthResponse.getErrorCode());
                            return Result.success(response);
                        }
                );
    }

    @PutMapping("/{username}")
    public Mono<Result<UserRegisterResponse>> updateUser(
            @PathVariable String username,
            @Valid @RequestBody UserRegisterRequest request) {
        // Set username from path variable (immutable, cannot be updated via body)
        request.setUserName(username);
        return userService.updateUser(toUserUpdateRequest(request))
                .map(result -> {
                    if (result.isSuccess()) {
                        UserRegisterResponse resp = toUserRegisterResponseFromUpdate(result.getData());
                        resp.setMessage("User updated successfully");
                        return Result.success(resp);
                    }
                    return Result.<UserRegisterResponse>error(result.getErrorCode(), result.getErrorMessage());
                });
    }

    private CreateUserAuthRequest convertToCreateUserAuthRequest(@Valid UserUserAuthRequest userUserAuthRequest) {
        CreateUserAuthRequest createUserAuthRequest = new CreateUserAuthRequest();
        createUserAuthRequest.setUserName(userUserAuthRequest.getUserName());
        createUserAuthRequest.setAuthToken(userUserAuthRequest.getAuthToken());
        createUserAuthRequest.setExpire(userUserAuthRequest.getExpire());
        return createUserAuthRequest;
    }

    private UserCreateRequest toUserCreateRequest(UserRegisterRequest request) {
        UserCreateRequest r = new UserCreateRequest();
        r.setUserName(request.getUserName());
        r.setEncryptedPassword(request.getEncryptedPassword());
        r.setFullName(request.getFullName());
        r.setLink(request.getProfileLink() != null ? request.getProfileLink().toString() : null);
        r.setRole(request.getRole());
        return r;
    }

    private UserUpdateRequest toUserUpdateRequest(UserRegisterRequest request) {
        UserUpdateRequest r = new UserUpdateRequest();
        r.setUserName(request.getUserName());  // Username is required for identification but won't be updated
        r.setEncryptedPassword(request.getEncryptedPassword());
        r.setFullName(request.getFullName());
        r.setLink(request.getProfileLink() != null ? request.getProfileLink().toString() : null);
        r.setRole(request.getRole());
        return r;
    }

    private UserDeleteRequest toUserDeleteRequest(UserRemoveRequest request) {
        UserDeleteRequest r = new UserDeleteRequest();
        r.setUserId(request.getUserId());
        return r;
    }

    private UserRegisterResponse toUserRegisterResponse(UserCreateResponse data) {
        UserRegisterResponse r = new UserRegisterResponse();
        r.setUserName(data.getUserName());
        r.setLink(data.getLink());
        r.setRole(data.getRole());
        r.setPassword(data.getPassword());
        return r;
    }

    private UserRegisterResponse toUserRegisterResponseFromUpdate(UserUpdateResponse data) {
        UserRegisterResponse r = new UserRegisterResponse();
        r.setUserName(data.getUserName());
        r.setLink(data.getLink());
        r.setRole(data.getRole());
        r.setPassword(data.getPassword());
        return r;
    }

    private UserRemoveResponse toUserRemoveResponse(UserDeleteResponse data) {
        UserRemoveResponse r = new UserRemoveResponse();
        r.setUserId(data.getUserId());
        r.setDeleted(data.getDeleted());
        r.setMessage("User deleted successfully");
        return r;
    }

    private FindUserResponse toFindUserResponse(FoundUserResponse data) {
        FindUserResponse r = new FindUserResponse();
        r.setUserName(data.getUserName());
        r.setEncodedPassword(data.getEncodedPassword());
        r.setName(data.getName());
        r.setEmail(data.getEmail());
        r.setRole(data.getRole());
        return r;
    }
}
