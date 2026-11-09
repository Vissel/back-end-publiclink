package com.qrpublic.apartment.authenAuthorisation.controller;

import com.qrpublic.apartment.adapter.authentication.request.NormalLoginRequest;
import com.qrpublic.apartment.adapter.authentication.response.NormalLoginResponse;
import com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.AuthenInterface;
import com.qrpublic.apartment.authenAuthorisation.template.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/server-auth")
public class AuthenticationController {
    @Autowired
    AuthenInterface authenInterface;

    @GetMapping("/public-key")
    public ResponseEntity<ByteArrayResource> getPublicKey() {
        try {
            // Read from /Users/user/.openssl/authpub.pem
            return ResponseEntity.ok(authenInterface.getPublicKey());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    @PostMapping("/normal")
    public ResponseEntity<NormalLoginResponse> normalLogin(@RequestBody NormalLoginRequest request) {
        com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request.NormalLoginRequest
                innerLoginRequest = toInnerLoginRequest(request);
        return convertToAdapterLoginResponse(authenInterface.normalLogin(innerLoginRequest));
    }

    private ResponseEntity<NormalLoginResponse> convertToAdapterLoginResponse(Result<com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.response.NormalLoginResponse> normalLoginResponseResult) {

        NormalLoginResponse adapterResponse = new NormalLoginResponse();
        if (normalLoginResponseResult.isSuccess()) {
            adapterResponse.setAuthenticated(normalLoginResponseResult.isSuccess());
            adapterResponse.setUsername(normalLoginResponseResult.getData().getUsername());
            adapterResponse.setToken(normalLoginResponseResult.getData().getToken());
            adapterResponse.setMessage("Login successful");
            adapterResponse.setValidTime(normalLoginResponseResult.getData().getValidTime());
            return ResponseEntity.ok(adapterResponse);
        }
        adapterResponse.setAuthenticated(false);
        adapterResponse.setMessage(normalLoginResponseResult.getErrorMessage());
        return ResponseEntity.status(normalLoginResponseResult.getErrorCode()).body(adapterResponse);
    }

    private com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request.NormalLoginRequest toInnerLoginRequest(NormalLoginRequest request) {
        com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request.NormalLoginRequest innerRequest =
                new com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request.NormalLoginRequest();
        innerRequest.setUsername(request.getUsername());
        innerRequest.setEncryptedPassword(request.getEncryptedPassword());
        innerRequest.setValidDuring(request.getValidDuring());
        return innerRequest;
    }

    public static <R> ResponseEntity<R> convert(
            Result<R> result) {

        if (result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        }
        NormalLoginResponse errorResponse = new NormalLoginResponse();
        errorResponse.setMessage(result.getErrorMessage());
        return ResponseEntity.status(result.getErrorCode()).body(null);
    }
}
