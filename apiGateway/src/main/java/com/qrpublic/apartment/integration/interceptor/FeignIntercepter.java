package com.qrpublic.apartment.integration.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignIntercepter implements RequestInterceptor {
    @Value("${server.ssl.key-store}")
    private String internalApiKey;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // Every call from Badminton to Auth Server includes this secret header
        requestTemplate.header("X-auth-System-ID123", "badminton-service");
        requestTemplate.header("X-auth-Secret", internalApiKey);
    }
}
