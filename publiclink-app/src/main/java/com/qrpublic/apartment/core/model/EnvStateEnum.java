package com.qrpublic.apartment.core.model;

import io.jsonwebtoken.lang.Assert;
import io.micrometer.common.util.StringUtils;
import lombok.Getter;

@Getter
public enum EnvStateEnum {
    CREATED, ACTIVE, INACTIVE, DELETED;

    public static EnvStateEnum fromString(String value) {
        try {
            Assert.isTrue(StringUtils.isNotBlank(value));
            return EnvStateEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
