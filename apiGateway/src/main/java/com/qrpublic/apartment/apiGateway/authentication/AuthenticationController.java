package com.qrpublic.apartment.apiGateway.authentication;

import com.qrpublic.apartment.apiGateway.authentication.request.BasicLoginRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.ChangePasswordRequest;
import com.qrpublic.apartment.apiGateway.authentication.request.RefreshTokenRequest;
import com.qrpublic.apartment.apiGateway.authentication.response.BasicLoginResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.ChangePasswordResponse;
import com.qrpublic.apartment.apiGateway.authentication.response.RefreshTokenResponse;
import com.qrpublic.apartment.apiGateway.exception.BusinessException;
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
    public Mono<ResponseEntity<BasicLoginResponse>> basicLogin(@RequestBody BasicLoginRequest request) {
        return authenticationService.basicLogin(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * First-login forced password change: consumes the single-use ticket issued
     * by /basic and replaces the generated password with the user's own.
     *
     * @param request Change password request (ticket + RSA-encrypted password)
     * @return Success response or mapped error status
     */
    @PostMapping("/change-password")
    public Mono<ResponseEntity<ChangePasswordResponse>> changePassword(@RequestBody ChangePasswordRequest request) {
        return authenticationService.changePassword(request)
                .map(ResponseEntity::ok)
                .onErrorResume(BusinessException.class, e -> {
                    HttpStatus status = e.getErrorCode() != null
                            ? e.getErrorCode().getHttpStatus()
                            : HttpStatus.BAD_REQUEST;
                    ChangePasswordResponse body = ChangePasswordResponse.builder()
                            .changed(false)
                            .message(e.getErrorCode() != null
                                    ? e.getErrorCode().getMessage()
                                    : "Change password failed")
                            .build();
                    return Mono.just(ResponseEntity.status(status).body(body));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    /**
     * Refresh access token using refresh token
     * Banking security: Implements token rotation (one-time use refresh tokens)
     *
     * @param request Refresh token request
     * @return New access and refresh token pair
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshTokenResponse>> refreshToken(@RequestBody RefreshTokenRequest request) {
        return authenticationService.refreshAccessToken(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    /**
     * Logout endpoint with token revocation
     * Banking security: Blacklists tokens to prevent reuse
     *
     * @param request Refresh token request
     * @return Logout success status
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<String>> logout(@RequestBody RefreshTokenRequest request) {
        return authenticationService.logout(request.getRefreshToken())
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok("Logout successful");
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Logout failed");
                    }
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Logout failed"));
    }


}
