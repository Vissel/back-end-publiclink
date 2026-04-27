package com.qrpublic.apartment.apiGateway.service.impl;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.service.SecurityCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SecurityCheckServiceImpl implements SecurityCheckService {

    @Autowired
    JwtTokenProducer jwtTokenProducer;

    @Override
    public Mono<Boolean> checkValidToken(String token) {
        return jwtTokenProducer.validateToken(token);
    }
}
