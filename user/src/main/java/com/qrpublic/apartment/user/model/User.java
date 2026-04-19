package com.qrpublic.apartment.user.model;

import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class User {

    private String userId;
    private String username;
    private String password;
    private String plainTextPassword; // For temporary storage before encoding
    private String fullName;
    private List<String> profileLinks; // List of profile links
    private String role;
    private Boolean isActive;

    public void setPlainTextPassword(String plainTextPassword) {
        if (Objects.nonNull(plainTextPassword)) {
            this.plainTextPassword = plainTextPassword;
        }
    }
}
