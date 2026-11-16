package com.qrpublic.apartment.apiGateway.gatewayConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
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

        /**
         * The gateway's own context path. Production puts nginx in front, which
         * strips /apiGateway before proxying, so the base path stays "/" and the
         * routes below forward the path unchanged. QA has no nginx: the gateway is
         * started with --spring.webflux.base-path=/apiGateway so the browser URLs
         * match production.
         *
         * <p>
         * Spring Cloud Gateway builds the downstream URL from the full request
         * URI, base path included. Without removing it here, every service receives
         * /apiGateway/publiclink/..., /apiGateway/api/v1/user/... or
         * /apiGateway/chat/... and answers 404 - which is exactly what the QA
         * browser saw for chat, avatar and the seller endpoints while the gateway's
         * own /api/v1/auth/** kept working.
         */
        @Value("${spring.webflux.base-path:/}")
        private String basePath;

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                .route("PublicLinkApplication", r -> routed(r, "/publiclink/**", publiclinkServiceUrl))
                                .route("UserApplication", r -> routed(r, "/api/v1/user/**", userServiceUrl))
                                // Zalo chat bridge. chat-app declares
                                // spring.webflux.base-path=/chat itself, so the path
                                // arrives unchanged once the gateway prefix is removed.
                                .route("ChatApplication", r -> routed(r, "/chat/**", chatServiceUrl))
                                .build();
        }

        /**
         * Match the path as the caller sent it (relative to the gateway context),
         * then hand the downstream only the part that belongs to it.
         */
        private Buildable<Route> routed(PredicateSpec predicate, String pattern, String target) {
                BooleanSpec spec = predicate.path(pattern);
                String prefix = normalizedBasePath();
                if (!prefix.isEmpty()) {
                        return spec.filters(f -> f.rewritePath(prefix + "/(?<segment>.*)", "/${segment}"))
                                        .uri(target);
                }
                return spec.uri(target);
        }

        /**
         * "" for "/" or blank, otherwise a single leading-slash prefix with no trailing
         * slash.
         */
        private String normalizedBasePath() {
                if (basePath == null) {
                        return "";
                }
                String trimmed = basePath.trim();
                if (trimmed.isEmpty() || "/".equals(trimmed)) {
                        return "";
                }
                if (!trimmed.startsWith("/")) {
                        trimmed = "/" + trimmed;
                }
                while (trimmed.endsWith("/") && trimmed.length() > 1) {
                        trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                return "/".equals(trimmed) ? "" : trimmed;
        }
}
