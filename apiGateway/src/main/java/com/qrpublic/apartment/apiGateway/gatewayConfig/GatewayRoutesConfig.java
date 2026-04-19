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
                .route("PublicLinkApplication", r -> r.path("/publiclink/**")
                        .uri("http://localhost:9080"))
                .route("UserApplication", r -> r.path("/api/v1/user/**")
//                        .filters(f -> f.rewritePath("/api/v1/user(?<segment>/?.*)", "/api/v1/user${segment}"))
                        .uri("http://localhost:8082"))
                .build();
    }
}
