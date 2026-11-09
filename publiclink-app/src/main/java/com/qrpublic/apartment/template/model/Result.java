package com.qrpublic.apartment.template.model;

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

}
