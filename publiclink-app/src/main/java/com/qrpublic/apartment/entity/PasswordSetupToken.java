package com.qrpublic.apartment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Single-use credential for the /buyer-setup link. purpose is SETUP (buyer
 * account created by a seller payment plan) or RESET (admin trigger from the
 * User List, any role). The link carries token_code only; consumption flips
 * used and the row is never reused.
 */
@Entity
@Table(name = "password_setup_token")
@Getter
@Setter
@NoArgsConstructor
public class PasswordSetupToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "token_code", nullable = false, unique = true, length = 36)
    private String tokenCode;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(name = "requested_by", length = 50)
    private String requestedBy;

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;
}
