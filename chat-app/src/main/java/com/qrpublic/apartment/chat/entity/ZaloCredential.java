package com.qrpublic.apartment.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Persisted Zalo credential / binding row, addressed by a stable scope key so a
 * rotating refresh token survives a service restart.
 *
 * Known scopes: OA_TOKEN (value_a = access token, value_b = refresh token,
 * expires_at = epoch millis), ADMIN_USER (value_a = admin Zalo user id).
 */
@Entity
@Table(name = "zalo_credential")
@Getter
@Setter
@NoArgsConstructor
public class ZaloCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_key", nullable = false, length = 64)
    private String scopeKey;

    @Column(name = "value_a", columnDefinition = "TEXT")
    private String valueA;

    @Column(name = "value_b", columnDefinition = "TEXT")
    private String valueB;

    @Column(name = "expires_at")
    private Long expiresAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
