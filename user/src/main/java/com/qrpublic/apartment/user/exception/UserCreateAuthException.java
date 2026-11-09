package com.qrpublic.apartment.user.exception;

import com.qrpublic.apartment.adapter.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class UserCreateAuthException extends BusinessException {

    public UserCreateAuthException(String username, Throwable cause) {
        super(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode(),
                UserErrorEnum.USER_CREATE_AUTH_ERROR.getMessage() + " for user: " + username, cause);
    }
}
