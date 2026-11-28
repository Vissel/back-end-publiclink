package com.qrpublic.apartment.account.response;

import lombok.Data;

/**
 * The account's own identity as the /self page shows it. Everything is read
 * from the caller's JWT-forwarded username, so the page can never be pointed
 * at somebody else's account.
 */
@Data
public class SelfProfileResponse {
    private String username;
    /** display name, editable */
    private String name;
    /** contact email; blank until the account adds it */
    private String email;
    /** SELLER or BUYER */
    private String role;
    /** true once an email is on file: the field is then read-only by design */
    private boolean emailLocked;
}
