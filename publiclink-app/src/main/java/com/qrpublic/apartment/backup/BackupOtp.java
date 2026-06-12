package com.qrpublic.apartment.backup;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "backup_otp")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupOtp {
    @Id @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "otp_code", nullable = false, length = 6) private String otpCode;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private OtpStatus status;
    @Column(name = "issued_at", nullable = false) private LocalDateTime issuedAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
    @Column(name = "backup_token") private String backupToken;
    @Column(name = "token_expires_at") private LocalDateTime tokenExpiresAt;
    public enum OtpStatus { ISSUED, VERIFIED, EXPIRED, USED }
}
