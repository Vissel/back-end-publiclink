package com.qrpublic.apartment.user.model;

import lombok.Data;

import java.util.Objects;

@Data
public class User {

    private String userName;
    private String plainTextPassword;
    private String fullName;
    private String profileLink;
    private UserRole userRole;
    private UserType userType;

    public void setPlainTextPassword(String plainTextPassword) {
        if (Objects.nonNull(plainTextPassword)) {
            this.plainTextPassword = plainTextPassword;
        }
    }
}
