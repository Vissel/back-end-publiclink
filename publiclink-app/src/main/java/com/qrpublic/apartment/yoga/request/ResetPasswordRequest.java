package com.qrpublic.apartment.yoga.request;

import lombok.Data;

/** Admin trigger reset-password from the User List. */
@Data
public class ResetPasswordRequest {
    private String username;
}
