package com.qrpublic.apartment.user.exception;

import com.qrpublic.apartment.adapter.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserDeleteException extends BusinessException {

    public UserDeleteException(UserErrorEnum userErrorEnum, String userId) {
        super(userErrorEnum.getCode(), userErrorEnum.getMessage() + " with id: " + userId);
    }

}
