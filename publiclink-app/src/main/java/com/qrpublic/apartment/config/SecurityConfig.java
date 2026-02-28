package com.qrpublic.apartment.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.qrpublic.apartment.authentication.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Autowired
	CustomUserDetailsService customUserDetailsService;

	@Autowired
	private JwtFilter jwtFilter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Disable CSRF for simpler development (be cautious in production)
				.csrf(csrf -> csrf.disable())
				// Configure CORS
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				// Authorize all requests (adjust as per your security requirements)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/login", "/index", "/error", "/admin/internal/**", "/auth/**", "/public/**",
                                "/api/v1/server-auth/**","/api/v1/user/**")
                        .permitAll()
						.requestMatchers("/admin/generator/**", "/api/generator/**").authenticated()
                        .anyRequest().authenticated()

                )
                // x509 compliance
                .x509(x509 -> x509
                        .subjectPrincipalRegex("(.*)") // Extracts the name from the Cert, (.*?)(?:,|$)
                )
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				// .formLogin(form -> form.loginProcessingUrl("/login")
//						.
//						.usernameParameter("username")
//						.passwordParameter("inputPassword").successHandler(authenticationSuccessHandler())
//						.failureHandler(authenticationFailureHandler()).permitAll())
				// Add other security configurations as needed (e.g., formLogin, httpBasic,
				// session management)
				// Configure logout
				.logout(logout -> logout.logoutUrl("/logout") // URL to trigger logout
						.logoutSuccessHandler((request, response, authentication) -> {
							response.setStatus(HttpServletResponse.SC_OK); // Return 200 OK
							response.getWriter().write("{\"message\": \"Logged out successfully\"}");
							response.getWriter().flush();
						}).invalidateHttpSession(true).deleteCookies("JSESSIONID")); // Or whatever your session cookie
																						// name is);
		return http.build();
	}

	@Bean
	AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(customUserDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder());
		return authenticationProvider;
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		// Allow all origins (use specific origins in production)
		configuration.setAllowedOriginPatterns(Arrays.asList("*"));
		// Allow all HTTP methods
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		// Allow all headers
		configuration.setAllowedHeaders(Arrays.asList("*"));
		// Allow credentials (e.g., cookies, authorization headers)
		configuration.setAllowCredentials(true);
		configuration.setExposedHeaders(List.of("token"));// set token
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// Apply this CORS configuration to all paths
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
	// Custom success handler to return JSON instead of redirecting
	@Bean
	AuthenticationSuccessHandler authenticationSuccessHandler() {
		return new AuthenticationSuccessHandler() {
			@Override
			public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
					Authentication authentication) throws IOException {
				response.setStatus(HttpServletResponse.SC_OK); // HTTP 200 OK
				response.setContentType("application/json");
				response.getWriter().write(
						"{\"message\": \"Login successful\", \"username\": \"" + authentication.getName() + "\"}");
				response.getWriter().flush();
				// If you use session, Spring Security will automatically set the JSESSIONID
				// cookie
			}
		};
	}

	// Custom failure handler to return JSON instead of redirecting
	@Bean
	AuthenticationFailureHandler authenticationFailureHandler() {
		return new AuthenticationFailureHandler() {
			@Override
			public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
					AuthenticationException exception) throws IOException {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 401 Unauthorized
				response.setContentType("application/json");
				response.getWriter().write("{\"message\": \"Authentication failed: " + exception.getMessage() + "\"}");
				response.getWriter().flush();
			}
		};
	}
}
