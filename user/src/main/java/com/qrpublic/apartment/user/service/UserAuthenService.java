package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
import com.qrpublic.apartment.user.service.response.FoundUserAuthenResponse;
import reactor.core.publisher.Mono;

public interface UserAuthenService {
    Mono<FoundUserAuthenResponse> findUserAuthByUsername(FoundUserAuthenRequest foundUserAuthenRequest);
}
