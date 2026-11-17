package com.qrpublic.apartment.user.response;

import lombok.Data;

/**
 * Admin create-user result. The setup link is always returned so the admin can
 * copy and forward it even when mail delivery is down, exactly like the
 * reset-password result.
 */
@Data
public class AdminCreateUserResponse {
    private String username;
    private String name;
    private String email;
    private String role;
    /** Absolute single-use link where the new user sets their own password. */
    private String setupLink;
    private String message;
}
