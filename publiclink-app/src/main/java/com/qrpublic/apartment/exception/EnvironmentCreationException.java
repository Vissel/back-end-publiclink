package com.qrpublic.apartment.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when sale environment creation fails
 */
public class EnvironmentCreationException extends ApplicationException {
    
    public EnvironmentCreationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public EnvironmentCreationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public EnvironmentCreationException(String message, int errorCode) {
        super(message, errorCode);
    }

    public EnvironmentCreationException(String message, Throwable cause, int errorCode) {
        super(message, cause, errorCode);
    }
}

