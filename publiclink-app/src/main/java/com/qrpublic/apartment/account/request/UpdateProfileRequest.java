package com.qrpublic.apartment.account.request;

import lombok.Data;

/**
 * Own-profile edit from the /self page. The name is always editable; the email
 * is only accepted while the account has none, after which it is fixed.
 */
@Data
public class UpdateProfileRequest {
    private String name;
    private String email;
}
