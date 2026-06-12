package com.qrpublic.apartment.backup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/backup/otp") @RequiredArgsConstructor
public class BackupOtpController {
    private final BackupOtpService backupOtpService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateOtp() {
        return ResponseEntity.ok(Map.of("sessionId", backupOtpService.generateOtp(), "message", "OTP sent"));
    }

    @GetMapping("/verify/{sessionId}/{otpCode}")
    public ResponseEntity<?> verifyOtp(@PathVariable String sessionId, @PathVariable String otpCode) {
        String token = backupOtpService.verifyOtp(sessionId, otpCode);
        if (token == null) return ResponseEntity.status(401).body(Map.of("error", "OTP verification failed"));
        return ResponseEntity.ok(Map.of("backupToken", token, "message", "OTP verified"));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("X-Backup-Token") String token) {
        return ResponseEntity.ok(Map.of("valid", backupOtpService.validateBackupToken(token)));
    }
}
