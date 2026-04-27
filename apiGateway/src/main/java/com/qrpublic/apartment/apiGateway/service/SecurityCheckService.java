package com.qrpublic.apartment.apiGateway.service;

import reactor.core.publisher.Mono;

public interface SecurityCheckService {
    Mono<Boolean> checkValidToken(String token);
}
