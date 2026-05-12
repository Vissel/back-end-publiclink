package com.qrpublic.apartment.core.coreException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;

public class EntityAssert {

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new EmptyResultDataAccessException(message, 1);
        }
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new EmptyResultDataAccessException(message, 1);
        }
    }

    public static void notEmpty(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new EmptyResultDataAccessException(message, 1);
        }
    }
}
