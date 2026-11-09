package com.qrpublic.apartment.adapter.template;

import lombok.Data;

@Data
public class Result<T> {
    /**
     * success result
     */
    private boolean success;

    /**
     * data
     */
    private T data;

    /**
     * error message
     */
    private String errorMessage;

    /**
     * error code
     */
    private int errorCode;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setErrorCode(0);
        result.setErrorMessage(null);
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorCode(code);
        result.setErrorMessage(message);
        result.setData(null);
        return result;
    }
}
