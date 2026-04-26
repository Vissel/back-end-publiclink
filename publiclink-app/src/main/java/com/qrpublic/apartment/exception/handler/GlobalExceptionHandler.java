package com.qrpublic.apartment.exception.handler;

import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.EnvironmentCreationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.exception.ValidationException;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.util.ErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Centralized exception handler for the entire application
 * Handles all exceptions and returns consistent error responses in reactive context
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle ApplicationException and its subclasses
     * This includes: EnvironmentCreationException, ResourceNotFoundException, ValidationException
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Result<?>> handleApplicationException(ApplicationException ex) {
        log.warn("Application exception occurred: {}", ex.getErrorMessage());
        Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
        HttpStatus status = ErrorHandler.getHttpStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(errorResult);
    }

    /**
     * Handle EnvironmentCreationException specifically (though it extends ApplicationException)
     */
    @ExceptionHandler(EnvironmentCreationException.class)
    public ResponseEntity<Result<?>> handleEnvironmentCreationException(EnvironmentCreationException ex) {
        log.error("Environment creation failed: {}", ex.getErrorMessage());
        Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
    }

    /**
     * Handle ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<?>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getErrorMessage());
        Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResult);
    }

    /**
     * Handle ValidationException
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<?>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getErrorMessage());
        Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
    }

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Request validation failed: {}", ex.getMessage());
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");

        Result<?> errorResult = ErrorHandler.buildErrorResult(errorMessage, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
    }

    /**
     * Handle type mismatch in request parameters
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch in parameter '{}': {}", ex.getName(), ex.getMessage());
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String errorMessage = String.format("Invalid value for parameter '%s': expected %s",
                ex.getName(), typeName);

        Result<?> errorResult = ErrorHandler.buildErrorResult(errorMessage, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
    }

    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        Result<?> errorResult = ErrorHandler.buildErrorResult(
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
    }
}


