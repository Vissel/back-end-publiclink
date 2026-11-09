package com.qrpublic.apartment.chat.template.model;

import lombok.Data;

/**
 * Service-layer result envelope of the chat module.
 *
 * Duplicated from publiclink-app on purpose: authen-authorisation and the user
 * module each keep their own Result, and sharing one copy would put a common
 * dependency between independently deployable services.
 */
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
