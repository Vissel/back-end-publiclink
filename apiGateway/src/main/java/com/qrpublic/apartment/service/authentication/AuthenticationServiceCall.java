package com.qrpublic.apartment.service.authentication;

import com.qrpublic.apartment.adapter.authentication.request.NormalLoginRequest;
import com.qrpublic.apartment.integration.AuthenticationClient;
import com.qrpublic.apartment.service.authentication.request.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationServiceCall {

    @Autowired
    private AuthenticationClient authenticationClient;

    public ResponseEntity<ByteArrayResource> getOnlinePublicKey() {
        return authenticationClient.getPublicKey();
    }

    public ResponseEntity<?> login(@Valid LoginRequest loginRequest) {
        NormalLoginRequest request = toNormalLoginRequest(loginRequest);
        return convertToResponseEntity(authenticationClient.normalLogin(request));
    }

    private NormalLoginRequest toNormalLoginRequest(LoginRequest loginRequest) {
        NormalLoginRequest request = new NormalLoginRequest();
        request.setUsername(loginRequest.getUsername());
        request.setEncryptedPassword(loginRequest.getPassword());
        return request;
    }

    private <T> ResponseEntity<?> convertToResponseEntity(ResponseEntity<T> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.ok(response.getBody());
        } else {
            // body = Result object with error code + error message
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        }
    }
}
