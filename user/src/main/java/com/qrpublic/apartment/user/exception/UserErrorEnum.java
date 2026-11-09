package com.qrpublic.apartment.user.exception;

import lombok.Getter;

@Getter
public enum UserErrorEnum {

    USER_DELETE_ERROR(430, "Error while deleting user"),
    USER_CREATE_AUTH_ERROR(431, "Error while creating user auth");

    private int code;
    private String message;

    UserErrorEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}