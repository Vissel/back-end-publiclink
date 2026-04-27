package com.qrpublic.apartment.user.exception;

import com.qrpublic.apartment.adapter.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String userId) {
        super(HttpStatus.NOT_FOUND.value(),"User not found with id: " + userId);
    }

}
