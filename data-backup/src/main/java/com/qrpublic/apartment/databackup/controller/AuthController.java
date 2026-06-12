package com.qrpublic.apartment.databackup.controller;
import com.qrpublic.apartment.databackup.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final OtpService otpService;

    @PostMapping("/token")
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String otpCode = request.get("otpCode");
        if (sessionId == null || otpCode == null)
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and otpCode are required"));
        String backupToken = otpService.verifyAndActivate(sessionId, otpCode);
        if (backupToken == null)
            return ResponseEntity.status(401).body(Map.of("error", "OTP verification failed"));
        return ResponseEntity.ok(Map.of("backupToken", backupToken, "message", "Authentication successful"));
    }
}
