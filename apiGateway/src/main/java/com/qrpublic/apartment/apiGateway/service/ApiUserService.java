package com.qrpublic.apartment.apiGateway.service;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.apiGateway.integration.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class ApiUserService implements ReactiveUserDetailsService {

    private static final String INVALID_USERNAME_MSG = "Username cannot be null or empty";
    private static final String USER_NOT_FOUND_MSG = "User not found: %s";
    private static final String INVALID_USER_DATA_MSG = "Invalid user data received";
    private static final String USER_MISMATCH_MSG = "Retrieved username %s does not match requested username: %s";

    @Autowired
    UserClient userClient;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() -> validateUsername(username))
                .flatMap(validUsername -> Mono.fromCallable(() ->
                        userClient.findUserByUsername(new FindUserRequest(validUsername))))
                .flatMap(response -> validateAndProcessResponse(response, username))
                .onErrorResume(UsernameNotFoundException.class, Mono::error)
                .onErrorResume(this::handleUnexpectedError)
                .doOnError(this::logError);
    }

    /**
     * Validates username and returns it if valid.
     *
     * @param username the username to validate
     * @return validated username
     * @throws IllegalArgumentException if username is invalid
     */
    private String validateUsername(String username) {
        try {
            Assert.hasText(username, INVALID_USERNAME_MSG);
            log.debug("Username validation passed: {}", username);
            return username;
        } catch (IllegalArgumentException e) {
            log.warn("Username validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Validates and processes the external service response.
     * Centralizes all response validation logic.
     *
     * @param response          the ResponseEntity from external service
     * @param requestedUsername the originally requested username
     * @return Mono containing UserDetails or error
     */
    private Mono<UserDetails> validateAndProcessResponse(ResponseEntity<FindUserResponse> response, String requestedUsername) {
        return Mono.fromCallable(() -> {
            try {
                // Validate response structure
                validateResponseStructure(response, requestedUsername);

                // Extract and validate user data
                FindUserResponse userResponse = response.getBody();
                validateUserResponse(userResponse, requestedUsername);

                // Build user details
                return buildUserDetails(userResponse);

            } catch (UsernameNotFoundException e) {
                log.warn("User validation failed for username: {} | Error: {}", requestedUsername, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error processing user response for username: {} | Error: {} | Cause: {}",
                        requestedUsername, e.getMessage(), e.getClass().getSimpleName(), e);
                throw new RuntimeException("Error processing user information from external service", e);
            }
        });
    }

    /**
     * Validates the HTTP response structure.
     * Groups: response object, status code checks.
     *
     * @param response the ResponseEntity to validate
     * @param username the username context for logging
     * @throws UsernameNotFoundException if response structure is invalid
     */
    private void validateResponseStructure(ResponseEntity<FindUserResponse> response, String username) {
        // Validate response is not null and has 2xx status
        try {
            Assert.notNull(response, String.format(USER_NOT_FOUND_MSG, username));
            Assert.isTrue(response.getStatusCode().is2xxSuccessful(),
                    String.format("Failed to retrieve user. Status: %s", response.getStatusCode()));

            // Validate response body is not null
            Assert.notNull(response.getBody(), String.format(USER_NOT_FOUND_MSG, username));

            log.debug("Response structure validation passed for username: {}", username);
        } catch (IllegalArgumentException e) {
            log.warn("Response validation failed for username: {}", username);
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
    }

    /**
     * Validates the user response data.
     * Groups: username, email, and data consistency checks.
     *
     * @param userResponse      the FindUserResponse to validate
     * @param requestedUsername the originally requested username
     * @throws UsernameNotFoundException if user data is invalid
     */
    private void validateUserResponse(FindUserResponse userResponse, String requestedUsername) throws UsernameNotFoundException {
        // Validate username in response
        String retrievedUsername = userResponse.getUserName();
        Assert.hasText(retrievedUsername, INVALID_USER_DATA_MSG);

        // Validate username matches request
        Assert.isTrue(retrievedUsername.equalsIgnoreCase(requestedUsername),
                String.format(USER_MISMATCH_MSG, retrievedUsername, requestedUsername));

        // Warn if email is missing (non-blocking)
//            if (!hasValidEmail(userResponse.getEmail())) {
//                log.warn("Retrieved user has no email for username: {}", retrievedUsername);
//            }

        log.debug("User response validation passed for username: {}", retrievedUsername);
    }

    /**
     * Builds UserDetails from validated user response.
     *
     * @param userResponse the validated FindUserResponse
     * @return UserDetails object for Spring Security
     */
    private UserDetails buildUserDetails(FindUserResponse userResponse) {
        String username = userResponse.getUserName();
        List<SimpleGrantedAuthority> authorities = extractAuthoritiesFromResponse(userResponse);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("")
                .authorities(authorities)
                .build();

        log.info("Successfully built UserDetails for: {} | Email: {}", username, userResponse.getEmail());
        return userDetails;
    }

    /**
     * Validates if email is present and not empty.
     * Helper method for email validation.
     *
     * @param email the email to validate
     * @return true if email is valid, false otherwise
     */
    private boolean hasValidEmail(String email) {
        return email != null && !email.trim().isEmpty();
    }

    /**
     * Handles unexpected errors in the stream.
     * Centralizes error conversion logic.
     *
     * @param error the error to handle
     * @return Mono with converted error
     */
    private Mono<UserDetails> handleUnexpectedError(Throwable error) {
        String errorType = error.getClass().getSimpleName();
        log.error("Unexpected error type: {} | Message: {}", errorType, error.getMessage());

        if (error instanceof UsernameNotFoundException) {
            return Mono.error(error);
        }

        return Mono.error(new UsernameNotFoundException("Unable to process user request: " + error.getMessage(), error));
    }

    /**
     * Centralized error logging.
     * Consolidates all error logging logic.
     *
     * @param error the error to log
     */
    private void logError(Throwable error) {
        if (error instanceof UsernameNotFoundException) {
            log.debug("Username not found: {}", error.getMessage());
        } else if (error instanceof IllegalArgumentException) {
            log.warn("Invalid input: {}", error.getMessage());
        } else {
            log.error("Error during user lookup: {}", error.getMessage(), error);
        }
    }

    /**
     * Extract authorities/roles from the user response.
     * Adapts the response data structure to Spring Security authorities.
     * Since FindUserResponse doesn't have explicit roles, we assign a default USER role
     * and apply additional logic based on business rules.
     *
     * @param userResponse the user response from external service
     * @return list of SimpleGrantedAuthority
     */
    private List<SimpleGrantedAuthority> extractAuthoritiesFromResponse(FindUserResponse userResponse) {
        try {
            // TODO: In production, query a role service based on username/email
            // For now, assign default USER role
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        } catch (Exception e) {
            log.debug("Failed to extract roles from user response: {}", e.getMessage());
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
