package com.qrpublic.apartment.authenAuthorisation.template.model.enums;

import lombok.Getter;

@Getter
public enum ResultEnum {
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR");

    private int code;
    private String description;

    ResultEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
