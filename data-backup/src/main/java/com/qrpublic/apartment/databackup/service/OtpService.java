package com.qrpublic.apartment.databackup.service;
import com.qrpublic.apartment.databackup.config.BackupProperties;
import com.qrpublic.apartment.databackup.entity.OtpSessionEntity;
import com.qrpublic.apartment.databackup.repository.OtpSessionRepository;
import com.qrpublic.apartment.databackup.security.BackupTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class OtpService {
    private final OtpSessionRepository otpSessionRepository;
    private final BackupProperties backupProperties;
    private final BackupTokenStore backupTokenStore;
    private final RestTemplate restTemplate;

    public String verifyAndActivate(String sessionId, String otpCode) {
        String url = backupProperties.getPubliclink().getServiceUrl() + "/api/backup/otp/verify/" + sessionId + "/" + otpCode;
        try {
            @SuppressWarnings("unchecked") Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null || resp.containsKey("error")) { log.warn("OTP verify failed"); return null; }
            String token = (String) resp.get("backupToken");
            if (token == null) return null;
            otpSessionRepository.save(OtpSessionEntity.builder().sessionId(sessionId).otpCode(otpCode)
                .status(OtpSessionEntity.OtpStatus.VERIFIED).issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(backupProperties.getOtp().getTtlMinutes()))
                .verifiedAt(LocalDateTime.now()).build());
            backupTokenStore.activateToken(token);
            return token;
        } catch (Exception e) { log.error("OTP verify error: {}", e.getMessage()); return null; }
    }
}
