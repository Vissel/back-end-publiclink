package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.authentication.request.FindUserAuthenRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserAuthenResponse;
import com.qrpublic.apartment.user.service.UserAuthenService;
import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
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
                });
    }

    private FoundUserAuthenRequest convertToFoundUserAuthRequest(FindUserAuthenRequest findUserAuthenRequest) {
        FoundUserAuthenRequest request = new FoundUserAuthenRequest();
        request.setUserName(findUserAuthenRequest.getUserName());
        return request;
    }
}
