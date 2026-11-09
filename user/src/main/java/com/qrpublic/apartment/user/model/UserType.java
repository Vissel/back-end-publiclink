package com.qrpublic.apartment.user.model;

import lombok.Getter;

@Getter
public enum UserType {
    ADMIN,
    SELLER,
    BUYER;

    public static UserType toUserType(String userType) {
        for (UserType type : UserType.values()) {
            if (type.name().equalsIgnoreCase(userType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid user type: " + userType);
    }
}
