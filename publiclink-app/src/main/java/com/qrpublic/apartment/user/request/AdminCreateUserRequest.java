package com.qrpublic.apartment.user.request;

import lombok.Data;

/**
 * Admin creates an account for somebody else from the User List. No password
 * travels: the account service generates one that is never returned, and the
 * new user sets their own through the single-use setup link this call issues.
 *
 * <p>
 * Only SELLER and BUYER can be created here. Admin accounts are provisioned
 * server-side, so the workspace UI cannot mint privileges for itself.
 */
@Data
public class AdminCreateUserRequest {

    private String username;

    private String name;

    /** Optional: with an email the setup link is also mailed to the new user. */
    private String email;

    /** SELLER | BUYER */
    private String role;
}
