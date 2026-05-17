package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserAuthenRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserAuthenResponse;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserUserAuthRequest;
import com.qrpublic.apartment.adapter.user.response.UserUserAuthResponse;
import com.qrpublic.apartment.user.service.UserAuthenService;
import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
import com.qrpublic.apartment.user.service.request.UpdateUserAuthRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/seller")
public class SellerAuthenticationController {

    @Autowired
    UserAuthenService userAuthenService;

    @PostMapping("/findByUsername")
    public Mono<FindUserAuthenResponse> findUserAuthByUsername(@RequestBody FindUserAuthenRequest findUserAuthenRequest) {
        FoundUserAuthenRequest request = convertToFoundUserAuthRequest(findUserAuthenRequest);
        return userAuthenService.findUserAuthByUsername(request)
                .map(user -> {
                    FindUserAuthenResponse response = new FindUserAuthenResponse();
                    response.setUserName(user.getUserName());
                    response.setAuthenticationToken(user.getAuthenticationToken());
                    response.setCreatedAt(user.getCreatedAt());
                    response.setExpiredAt(user.getExpiredAt());
                    response.setIsActive(user.getIsActive());
                    response.setExtendedNum(user.getExtendedNum());
                    return response;
                })
                .defaultIfEmpty(new FindUserAuthenResponse());
    }

    @PostMapping("/updateUserAndUserAuth")
    public Mono<Result<UserUserAuthResponse>> updateUserAndUserAuth(@Valid @RequestBody UserUserAuthRequest userUserAuthRequest) {
        UpdateUserAuthRequest updateUserAuthRequest = convertToUpdateUserAuthRequest(userUserAuthRequest);
        return userAuthenService.updateUserAuth(updateUserAuthRequest).map(
                updateUserAuthResponse -> {
                    if (!Boolean.TRUE.equals(updateUserAuthResponse.getSuccess())) {
                        return Result.error(updateUserAuthResponse.getErrorCode(), updateUserAuthResponse.getErrorMessage());
                    }
                    UserUserAuthResponse response = new UserUserAuthResponse();
                    response.setSuccess(updateUserAuthResponse.getSuccess());
                    return Result.success(response);
                });
    }

    @PostMapping("/invalidateUserAuth")
    public Mono<Result<UserUserAuthResponse>> invalidateUserAuth(@Valid @RequestBody UserUserAuthRequest invalidUserAuthRequest) {
        return userAuthenService.invalidateUserAuthByUsername(invalidUserAuthRequest.getUserName())
                .map(updateUserAuthResponse -> {
                    if (!Boolean.TRUE.equals(updateUserAuthResponse.getSuccess())) {
                        return Result.error(updateUserAuthResponse.getErrorCode(), updateUserAuthResponse.getErrorMessage());
                    }
                    UserUserAuthResponse response = new UserUserAuthResponse();
                    response.setSuccess(updateUserAuthResponse.getSuccess());
                    return Result.success(response);
                });
    }

    private UpdateUserAuthRequest convertToUpdateUserAuthRequest(@Valid UserUserAuthRequest userUserAuthRequest) {
        UpdateUserAuthRequest updateUserAuthRequest = new UpdateUserAuthRequest();
        updateUserAuthRequest.setUserName(userUserAuthRequest.getUserName());
        updateUserAuthRequest.setAuthToken(userUserAuthRequest.getAuthToken());
        updateUserAuthRequest.setExpire(userUserAuthRequest.getExpire());
        return updateUserAuthRequest;
    }

    private FoundUserAuthenRequest convertToFoundUserAuthRequest(FindUserAuthenRequest findUserAuthenRequest) {
        FoundUserAuthenRequest request = new FoundUserAuthenRequest();
        request.setUserName(findUserAuthenRequest.getUserName());
        return request;
    }
}
