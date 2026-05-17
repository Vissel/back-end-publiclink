package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
import com.qrpublic.apartment.user.service.request.UpdateUserAuthRequest;
import com.qrpublic.apartment.user.service.response.FoundUserAuthenResponse;
import com.qrpublic.apartment.user.service.response.UpdateUserAuthResponse;
import reactor.core.publisher.Mono;

public interface UserAuthenService {
    Mono<FoundUserAuthenResponse> findUserAuthByUsername(FoundUserAuthenRequest foundUserAuthenRequest);

    Mono<UpdateUserAuthResponse> updateUserAuth(UpdateUserAuthRequest updateUserAuthRequest);

    Mono<UpdateUserAuthResponse> invalidateUserAuthByUsername(String username);
}
