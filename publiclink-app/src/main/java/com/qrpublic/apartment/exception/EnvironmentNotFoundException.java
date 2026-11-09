package com.qrpublic.apartment.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when sale environment creation fails
 */
public class EnvironmentNotFoundException extends ApplicationException {

    public EnvironmentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND.value());
    }

    public EnvironmentNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND.value());
    }

    public EnvironmentNotFoundException(String message, int errorCode) {
        super(message, errorCode);
    }

    public EnvironmentNotFoundException(String message, Throwable cause, int errorCode) {
        super(message, cause, errorCode);
    }
}

