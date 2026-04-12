package com.qrpublic.apartment.user.service;

import org.springframework.stereotype.Service;

@Service
public class PasswordMasker {
    public static String maskPassword(String password) {
        if (password == null || password.length() <= 3) {
            return password;
        }
        // Repeat '*' for all but the last 3 characters
        int maskLength = password.length() - 3;
        return "*".repeat(maskLength) + password.substring(maskLength);
    }

}
