package com.qrpublic.apartment.exception;

/**
 * Base exception class for all application-specific exceptions
 */
public class ApplicationException extends RuntimeException {
    private int errorCode;
    private String errorMessage;

    public ApplicationException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    public ApplicationException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

