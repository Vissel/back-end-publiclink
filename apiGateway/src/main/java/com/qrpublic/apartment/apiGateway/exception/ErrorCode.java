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
    PASSWORD_CHANGE_TICKET_INVALID("AUTH_007", "Invalid or expired password change ticket", HttpStatus.UNAUTHORIZED),
    /**
     * The RSA public key file is missing/unreadable: nobody can encrypt a password.
     */
    RSA_KEY_UNAVAILABLE("AUTH_008", "The gateway cannot load its RSA public key", HttpStatus.SERVICE_UNAVAILABLE),
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),

    /** A route target could not be reached: DNS, connection refused, timeout. */
    SERVICE_UNAVAILABLE("SVC_001", "A downstream service is unreachable", HttpStatus.SERVICE_UNAVAILABLE),
    /** Anything unexpected that is neither an auth nor a downstream failure. */
    INTERNAL_ERROR("SYS_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_ARGUMENTS("410", "Invalid arguments provided", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}