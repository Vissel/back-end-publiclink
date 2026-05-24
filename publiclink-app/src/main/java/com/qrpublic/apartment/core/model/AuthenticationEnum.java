package com.qrpublic.apartment.core.model;

import lombok.Getter;

@Getter
public enum AuthenticationEnum {
    BASIC,
    UNAUTHENTICATED;

    public static AuthenticationEnum fromString(String value) {
        try {
            return AuthenticationEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return UNAUTHENTICATED;
        }
    }
}
