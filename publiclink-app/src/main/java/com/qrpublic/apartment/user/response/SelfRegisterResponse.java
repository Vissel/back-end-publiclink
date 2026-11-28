package com.qrpublic.apartment.user.response;

import lombok.Data;

/**
 * Result of an open self-registration. The same shape carries a refusal: the
 * HTTP status is the machine-readable part and {@code message} is what the
 * register screen shows the visitor.
 */
@Data
public class SelfRegisterResponse {
    private String username;
    private String name;
    private String email;
    private String message;
    /**
     * Which input caused a refusal, when one of them did: {@code username},
     * {@code email} or {@code name}. Null on success and on failures that belong
     * to no single field, so the form can mark the right box instead of only
     * showing a banner.
     */
    private String field;
}
