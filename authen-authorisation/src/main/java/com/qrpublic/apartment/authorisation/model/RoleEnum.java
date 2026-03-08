package com.qrpublic.apartment.authorisation.model;

import lombok.Getter;

@Getter
public enum RoleEnum {
    ADMIN("Admin"), USER("User"), SELLER("Seller"), BUYER("Buyer"),
    BADMINTON_ADMIN("Badminton_Admin");

    private String role;

    RoleEnum(String role) {
        this.role = role;
    }

    public static boolean validRole(String r) {
        for (RoleEnum role : RoleEnum.values()) {
            if (role.getRole().equals(r)) {
                return true;
            }
        }
        return false;
    }
    public static RoleEnum getRoleEnum(String r) {
        for (RoleEnum role : RoleEnum.values()) {
            if (role.getRole().equals(r)) {
                return role;
            }
        }
        throw new RuntimeException("RoleEnum is not existed.");
    }
}
