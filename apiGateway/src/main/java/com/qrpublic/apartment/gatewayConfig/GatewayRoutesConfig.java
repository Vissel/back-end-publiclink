package com.qrpublic.apartment.gatewayConfig;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("authen-authorisation", r -> r.path("/**")
                        .uri("http://localhost:8081/"))
//            .route("user_service", r -> r.path("/user/**")
//                .uri("lb://USER-SERVICE"))
//            .route("booking_service", r -> r.path("/booking/**")
//                .uri("lb://BOOKING-SERVICE"))
                .build();
    }
}
