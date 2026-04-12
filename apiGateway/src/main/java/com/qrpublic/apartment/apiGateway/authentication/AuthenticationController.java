package com.qrpublic.apartment.apiGateway.authentication;

import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    @Autowired
    AuthenticationService authenticationService;

    @GetMapping("/public-key")
    public ResponseEntity<ByteArrayResource> getPublicKey() {
        try {
            // Read from /Users/user/.openssl/authpub.pem
            return ResponseEntity.ok(authenticationService.getPublicKey());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    @PostMapping("/basic")
    public Mono<ResponseEntity<BasicLoginResponse>> basicLogin(@Valid @RequestBody BasicLoginRequest request) {
        return authenticationService.basicLogin(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


}
