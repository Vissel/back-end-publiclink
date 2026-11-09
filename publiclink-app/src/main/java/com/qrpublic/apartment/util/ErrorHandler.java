package com.qrpublic.apartment.util;

import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.template.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Utility class for handling errors and building error responses
 */
@Slf4j
public class ErrorHandler {

    /**
     * Build error result from exception
     * @param exception the exception to handle
     * @return Result object with error details
     */
    public static <T> Result<T> buildErrorResult(Throwable exception) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setData(null);

        if (exception instanceof ApplicationException) {
            ApplicationException appEx = (ApplicationException) exception;
            result.setErrorCode(appEx.getErrorCode());
            result.setErrorMessage(appEx.getErrorMessage());
            log.warn("Application error occurred: {}", appEx.getErrorMessage());
        } else {
            result.setErrorCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            result.setErrorMessage("An unexpected error occurred. Please try again later.");
            log.error("Unexpected error occurred", exception);
        }

        return result;
    }

    /**
     * Build error result with custom message
     * @param message error message
     * @param errorCode HTTP status code
     * @return Result object with error details
     */
    public static <T> Result<T> buildErrorResult(String message, int errorCode) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setData(null);
        result.setErrorCode(errorCode);
        result.setErrorMessage(message);
        log.warn("Error response: {} ({})", message, errorCode);
        return result;
    }

    /**
     * Build success result
     * @param data the data to return
     * @return Result object with success status and data
     */
    public static <T> Result<T> buildSuccessResult(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setErrorCode(HttpStatus.OK.value());
        return result;
    }

    /**
     * Get HTTP status from error code
     * @param errorCode the error code
     * @return HttpStatus corresponding to the error code
     */
    public static HttpStatus getHttpStatus(int errorCode) {
        try {
            return HttpStatus.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}

