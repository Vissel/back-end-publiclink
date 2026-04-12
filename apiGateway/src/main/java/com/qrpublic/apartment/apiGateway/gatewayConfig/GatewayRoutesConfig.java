package com.qrpublic.apartment.apiGateway.gatewayConfig;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("PublicLinkApplicatione", r -> r.path("/publiclink/**")
                        .uri("http://localhost:9080/publiclink"))
//            .route("booking_service", r -> r.path("/booking/**")
//                .uri("lb://BOOKING-SERVICE"))
                .build();
    }
}
