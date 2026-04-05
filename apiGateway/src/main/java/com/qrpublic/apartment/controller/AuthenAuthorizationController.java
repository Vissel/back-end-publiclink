package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.service.authentication.AuthenticationServiceCall;
import com.qrpublic.apartment.service.authentication.request.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/server-auth")
public class AuthenAuthorizationController {

    @Autowired
    AuthenticationServiceCall authenInterface;

    @GetMapping("/public-key")
    public ResponseEntity<ByteArrayResource> getPublicKey() {
        try {
            // Read from /Users/user/.openssl/authpub.pem
            return authenInterface.getOnlinePublicKey();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    @PostMapping("/normalLogin")
    public ResponseEntity<?> normalLogin(@RequestBody LoginRequest loginRequest) {
        return authenInterface.login(loginRequest);
    }

}
