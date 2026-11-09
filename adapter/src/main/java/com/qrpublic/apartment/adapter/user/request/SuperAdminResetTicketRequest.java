package com.qrpublic.apartment.adapter.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Operator-side recovery for a user stuck in the first-login flow, e.g. when
 * the password-change ticket expired and the first password is no longer known.
 * The service re-issues the first password and re-arms mustChangePassword.
 */
@Data
public class SuperAdminResetTicketRequest {
    @NotBlank
    private String userName;
}
