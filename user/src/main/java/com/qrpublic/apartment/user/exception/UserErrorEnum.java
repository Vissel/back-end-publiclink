package com.qrpublic.apartment.user.exception;

import lombok.Getter;

@Getter
public enum UserErrorEnum {

    USER_DELETE_ERROR(430, "Error while deleting user");

    private int code;
    private String message;

    UserErrorEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}