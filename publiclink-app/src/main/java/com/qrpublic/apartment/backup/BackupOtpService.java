package com.qrpublic.apartment.backup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class BackupOtpService {
    private final BackupOtpRepository backupOtpRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${backup.otp.ttl-minutes:2}") private int otpTtlMinutes;
    @Value("${backup.otp.recipient:jelly1512@proton.me}") private String otpRecipient;
    @Value("${backup.token.ttl-minutes:5}") private int tokenTtlMinutes;

    @Transactional
    public String generateOtp() {
        String sessionId = UUID.randomUUID().toString();
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        backupOtpRepository.save(BackupOtp.builder().sessionId(sessionId).otpCode(otpCode)
            .status(BackupOtp.OtpStatus.ISSUED).issuedAt(now).expiresAt(now.plusMinutes(otpTtlMinutes)).build());
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(otpRecipient); msg.setSubject("[PublicLink Backup] OTP Code");
            msg.setText("Your backup OTP code: " + otpCode + "\n\nExpires in " + otpTtlMinutes + " minutes.");
            mailSender.send(msg);
        } catch (Exception e) { log.error("OTP email failed: {}", e.getMessage()); }
        return sessionId;
    }

    @Transactional
    public String verifyOtp(String sessionId, String otpCode) {
        BackupOtp otp = backupOtpRepository.findById(sessionId).orElse(null);
        if (otp == null) return null;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(otp.getExpiresAt())) { otp.setStatus(BackupOtp.OtpStatus.EXPIRED); backupOtpRepository.save(otp); return null; }
        if (otp.getStatus() != BackupOtp.OtpStatus.ISSUED) return null;
        if (!otp.getOtpCode().equals(otpCode)) return null;
        String token = UUID.randomUUID().toString();
        otp.setStatus(BackupOtp.OtpStatus.USED); otp.setVerifiedAt(now);
        otp.setBackupToken(token); otp.setTokenExpiresAt(now.plusMinutes(tokenTtlMinutes));
        backupOtpRepository.save(otp);
        return token;
    }

    @Transactional(readOnly = true)
    public boolean validateBackupToken(String token) {
        if (token == null || token.isBlank()) return false;
        return backupOtpRepository.findAll().stream()
            .anyMatch(o -> token.equals(o.getBackupToken()) && o.getTokenExpiresAt() != null
                && LocalDateTime.now().isBefore(o.getTokenExpiresAt()) && o.getStatus() == BackupOtp.OtpStatus.USED);
    }
}
