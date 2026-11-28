package com.qrpublic.apartment.comment.exception;

/**
 * Base exception of the comment module; {@code errorCode} doubles as the HTTP
 * status the service template puts on the response (chat-app's arrangement).
 */
public class ApplicationException extends RuntimeException {
    private final int errorCode;
    private final String errorMessage;

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
