package com.qrpublic.apartment.user.model;

import lombok.Getter;

@Getter
public enum UserRole {
    ROLE_ADMIN,
    ROLE_SELLER,
    ROLE_BUYER;

    public static UserRole toUserRole(String userRole) {
        for (UserRole role : UserRole.values()) {
            if (role.name().equalsIgnoreCase(userRole)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid user role: " + userRole);
    }
}
