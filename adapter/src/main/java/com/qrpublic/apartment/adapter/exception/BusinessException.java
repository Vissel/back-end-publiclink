package com.qrpublic.apartment.adapter.exception;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private int errorCode;

    public BusinessException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
