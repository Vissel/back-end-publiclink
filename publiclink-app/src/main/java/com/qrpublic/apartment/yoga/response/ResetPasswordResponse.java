package com.qrpublic.apartment.yoga.response;

import lombok.Data;

/** Admin reset-password result: the copyable setup link. */
@Data
public class ResetPasswordResponse {
    private String username;
    private String setupLink;
}
