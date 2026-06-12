package com.qrpublic.apartment.databackup.security;
import com.qrpublic.apartment.databackup.config.BackupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component @RequiredArgsConstructor @Slf4j
public class BackupTokenStore {
    private final BackupProperties backupProperties;
    private final ConcurrentHashMap<String, Instant> activeTokens = new ConcurrentHashMap<>();
    public void activateToken(String token) {
        activeTokens.put(token, Instant.now().plusSeconds(backupProperties.getToken().getTtlMinutes() * 60L));
    }
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Instant expiry = activeTokens.get(token);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) { activeTokens.remove(token); return false; }
        return true;
    }
    public void revokeToken(String token) { activeTokens.remove(token); }
}
