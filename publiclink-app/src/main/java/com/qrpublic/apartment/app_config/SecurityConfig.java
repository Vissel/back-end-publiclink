package com.qrpublic.apartment.app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
//@EnableWebSecurity
//@EnableMethodSecurity
public class SecurityConfig implements WebFluxConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/static/**")
//                .addResourceLocations("classpath:/static/");
    }

    //    @Bean
//    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                // Disable CSRF for simpler development (be cautious in production)
//                .csrf(csrf -> csrf.disable())
//                // Configure CORS
//                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//                // Authorize all requests (adjust as per your security requirements)
//                .authorizeHttpRequests(authorize -> authorize
//                        .requestMatchers("/login", "/index", "/error", "/admin/internal/**", "/auth/**", "/public/**",
//                                "/csrf-simulation/**", // for csrf simulation
//                                "/api/v1/server-auth/**", "/api/v1/user/**")
//                        .permitAll()
//                        .requestMatchers("/admin/generator/**", "/api/generator/**").authenticated()
//                        .anyRequest().authenticated()
//
//                )
//                // x509 compliance
//                .x509(x509 -> x509
//                        .subjectPrincipalRegex("(.*)") // Extracts the name from the Cert, (.*?)(?:,|$)
//                )
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .logout(logout -> logout.logoutUrl("/logout") // URL to trigger logout
//                        .logoutSuccessHandler((request, response, authentication) -> {
//                            response.setStatus(HttpServletResponse.SC_OK); // Return 200 OK
//                            response.getWriter().write("{\"message\": \"Logged out successfully\"}");
//                            response.getWriter().flush();
//                        }).invalidateHttpSession(true).deleteCookies("JSESSIONID")); // Or whatever your session cookie
//        // name is);
//        return http.build();
//    }
//
//    @Bean
//    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
//        return config.getAuthenticationManager();
//    }
//
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
//
//    @Bean
//    CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//        // Allow all origins (use specific origins in production)
//        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
//        // Allow all HTTP methods
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
//        // Allow all headers
//        configuration.setAllowedHeaders(Arrays.asList("*"));
//        // Allow credentials (e.g., cookies, authorization headers)
//        configuration.setAllowCredentials(true);
//        configuration.setExposedHeaders(List.of("token"));// set token
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        // Apply this CORS configuration to all paths
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }
//
//    // Custom success handler to return JSON instead of redirecting
//    @Bean
//    AuthenticationSuccessHandler authenticationSuccessHandler() {
//        return new AuthenticationSuccessHandler() {
//            @Override
//            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
//                                                Authentication authentication) throws IOException {
//                response.setStatus(HttpServletResponse.SC_OK); // HTTP 200 OK
//                response.setContentType("application/json");
//                response.getWriter().write(
//                        "{\"message\": \"Login successful\", \"username\": \"" + authentication.getName() + "\"}");
//                response.getWriter().flush();
//                // If you use session, Spring Security will automatically set the JSESSIONID
//                // cookie
//            }
//        };
//    }
//
//    // Custom failure handler to return JSON instead of redirecting
//    @Bean
//    AuthenticationFailureHandler authenticationFailureHandler() {
//        return new AuthenticationFailureHandler() {
//            @Override
//            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
//                                                AuthenticationException exception) throws IOException {
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 401 Unauthorized
//                response.setContentType("application/json");
//                response.getWriter().write("{\"message\": \"Authentication failed: " + exception.getMessage() + "\"}");
//                response.getWriter().flush();
//            }
//        };
//    }
}
