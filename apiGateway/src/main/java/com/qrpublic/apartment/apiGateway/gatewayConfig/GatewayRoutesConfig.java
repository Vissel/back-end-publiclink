package com.qrpublic.apartment.apiGateway.gatewayConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

        @Value("${publiclink.service.url:http://localhost:9080}")
        private String publiclinkServiceUrl;

        @Value("${user.service.url:http://localhost:8082}")
        private String userServiceUrl;

        @Value("${chat.service.url:http://localhost:9090}")
        private String chatServiceUrl;

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                .route("PublicLinkApplication", r -> r.path("/publiclink/**")
                                                .uri(publiclinkServiceUrl))
                                .route("UserApplication", r -> r.path("/api/v1/user/**")
                                                // .filters(f -> f.rewritePath("/api/v1/user(?<segment>/?.*)",
                                                // "/api/v1/user${segment}"))
                                                .uri(userServiceUrl))
                                // Zalo chat bridge. No RewritePath here: chat-app declares
                                // spring.webflux.base-path=/chat itself, so the path arrives unchanged.
                                .route("ChatApplication", r -> r.path("/chat/**")
                                                .uri(chatServiceUrl))
                                .build();
        }
}
