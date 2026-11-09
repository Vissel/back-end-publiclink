package com.qrpublic.apartment.apiGateway.gatewayConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    private static final int MAX_IN_MEMORY_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BUFFER_SIZE);
    }
}
