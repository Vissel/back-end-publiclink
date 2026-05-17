package com.qrpublic.apartment.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found
 */
@Slf4j
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        log.error("ResourceNotFoundException: errorCode:{}, message:{}", HttpStatus.NOT_FOUND, message);
        super(message, HttpStatus.NOT_FOUND.value());
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND.value());
    }
}

