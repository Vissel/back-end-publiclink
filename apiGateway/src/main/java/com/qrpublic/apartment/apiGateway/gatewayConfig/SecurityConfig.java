package com.qrpublic.apartment.apiGateway.gatewayConfig;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

//@Configuration
public class SecurityConfig {
    // Gateway-specific security configuration with JWT support (Reactive)
//    @Autowired(required = false)
//    private JwtFilter jwtFilter;

    //    @Bean
//    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//        http
//                // Disable CSRF for simpler development (be cautious in production)
//                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//                // Configure CORS
//                .cors(cors -> cors.configurationSource(corsReactiveConfigurationSource()))
//                // Authorize all requests
//                .authorizeExchange(exchanges -> exchanges
//                        .pathMatchers("/login", "/index", "/error", "/admin/internal/**", "/auth/**", "/public/**",
//                                "/csrf-simulation/**",
//                                "/api/v1/server-auth/**", "/api/v1/user/**")
//                        .permitAll()
//                        .pathMatchers("/admin/generator/**", "/api/generator/**").authenticated()
//                        .anyExchange().authenticated()
//                );
//
//        // Note: JwtFilter is servlet-based, not reactive. For reactive stack, implement a WebFilter instead
//        // if (jwtFilter != null) {
//        //     http.addFilterAt((exchange, chain) -> jwtFilter.filter(exchange, chain),
//        //             org.springframework.security.web.server.authentication.AuthenticationWebFilter.class);
//        // }
//
//        return http.build();
//    }

    //    @Bean
    CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    private UrlBasedCorsConfigurationSource corsReactiveConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
