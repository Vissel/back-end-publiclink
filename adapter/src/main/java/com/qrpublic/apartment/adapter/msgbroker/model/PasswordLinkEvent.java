package com.qrpublic.apartment.adapter.msgbroker.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Broker event carrying a password setup/reset link. Produced when a seller
 * payment plan provisions a buyer account (SETUP) or an admin triggers a
 * reset from the User List (RESET); consumed by the notification listener
 * which emails the link when an address is on file.
 */
@Data
public class PasswordLinkEvent {
    private String messageId;
    /** account the link belongs to */
    private String username;
    /** display name for the email body */
    private String fullName;
    /** nullable: without it the link is only shown copyable in the UI */
    private String email;
    /** absolute frontend URL including the single-use token */
    private String setupLink;
    /** SETUP | RESET */
    private String purpose;
    /** seller to notify (SETUP) */
    private String sellerUsername;
    /** admin who triggered the reset (RESET) */
    private String requestedBy;
    private LocalDateTime timestamp;
}
