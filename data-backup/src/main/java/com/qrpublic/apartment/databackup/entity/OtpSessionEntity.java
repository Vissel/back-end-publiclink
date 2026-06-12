package com.qrpublic.apartment.databackup.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_session_tbl")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpSessionEntity {
    @Id @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "otp_hash", length = 128)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OtpStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    public enum OtpStatus { ISSUED, VERIFIED, EXPIRED, USED }
}
