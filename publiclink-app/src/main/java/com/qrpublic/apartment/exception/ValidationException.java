package com.qrpublic.apartment.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when request validation fails
 */
public class ValidationException extends ApplicationException {
    
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST.value());
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST.value());
    }
}

