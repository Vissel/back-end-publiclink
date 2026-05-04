package com.qrpublic.apartment.apiGateway.service;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.apiGateway.integration.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String INVALID_USER_DATA_MSG = "Invalid user data received";
    private static final String USER_MISMATCH_MSG = "Retrieved username %s does not match requested username: %s";

    @Autowired
    UserClient userClient;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        try {
            Assert.hasText(username, INVALID_USERNAME_MSG);
        } catch (IllegalArgumentException e) {
            return Mono.error(new UsernameNotFoundException(INVALID_USERNAME_MSG, e));
        }
        return userClient.findUserByUsername(new FindUserRequest(username))
                .flatMap(response -> validateAndBuild(response, username))
                .onErrorResume(UsernameNotFoundException.class, Mono::error)
                .onErrorResume(this::handleUnexpectedError)
                .doOnError(this::logError);
    }

    private Mono<UserDetails> validateAndBuild(FindUserResponse response, String requestedUsername) {
        return Mono.fromCallable(() -> {
            String retrievedUsername = response.getUserName();
            try {
                Assert.hasText(retrievedUsername, INVALID_USER_DATA_MSG);
                Assert.isTrue(retrievedUsername.equalsIgnoreCase(requestedUsername),
                        String.format(USER_MISMATCH_MSG, retrievedUsername, requestedUsername));
            } catch (IllegalArgumentException e) {
                throw new UsernameNotFoundException(e.getMessage(), e);
            }
            return buildUserDetails(response);
        });
    }

    private UserDetails buildUserDetails(FindUserResponse response) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(response.getRole()));
        log.info("Successfully built UserDetails for: {} | Role: {}", response.getUserName(), response.getRole());
        return org.springframework.security.core.userdetails.User
                .withUsername(response.getUserName())
                .password(response.getEncodedPassword())
                .authorities(authorities)
                .build();
    }

    private Mono<UserDetails> handleUnexpectedError(Throwable error) {
        log.error("Unexpected error type: {} | Message: {}", error.getClass().getSimpleName(), error.getMessage());
        return Mono.error(new UsernameNotFoundException("Unable to process user request: " + error.getMessage(), error));
    }

    private void logError(Throwable error) {
        if (error instanceof UsernameNotFoundException) {
            log.debug("Username not found: {}", error.getMessage());
        } else if (error instanceof IllegalArgumentException) {
            log.warn("Invalid input: {}", error.getMessage());
        } else {
            log.error("Error during user lookup: {}", error.getMessage(), error);
        }
    }
}
