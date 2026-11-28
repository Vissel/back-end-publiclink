package com.qrpublic.apartment.comment.template.model;

import lombok.Data;

/**
 * Service-layer result envelope of the comment module.
 *
 * Duplicated from publiclink-app on purpose, exactly like chat-app and the user
 * module keep their own copies: sharing one Result would put a common
 * dependency between independently deployable services.
 */
@Data
public class Result<T> {
    private boolean success;
    private T data;
    private String errorMessage;
    private int errorCode;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorCode(code);
        result.setErrorMessage(message);
        return result;
    }
}
