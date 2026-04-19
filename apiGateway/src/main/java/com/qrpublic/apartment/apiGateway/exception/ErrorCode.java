package com.qrpublic.apartment.apiGateway.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTHENTICATION_FAILED("AUTH_001", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH_002", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_003", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    LOGOUT_FAILED("AUTH_004", "Logout failed", HttpStatus.INTERNAL_SERVER_ERROR),
    REFRESH_TOKEN_FAILED("AUTH_005", "Token refresh failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCESS_DENIED("AUTH_006", "Access denied", HttpStatus.FORBIDDEN),
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),

    INVALID_ARGUMENTS("410", "Invalid arguments provided", HttpStatus.BAD_REQUEST);
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}