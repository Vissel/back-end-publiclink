package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.authentication.interfaces.AuthenInterface;
import com.qrpublic.apartment.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.template.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/server-auth")
public class AuthenAuthorizationController {

    @Autowired
    AuthenInterface authenInterface;

    @GetMapping("/public-key")
    public ResponseEntity<ByteArrayResource> getPublicKey() {
        try {
            // Read from /Users/user/.openssl/authpub.pem
            return ResponseEntity.ok(authenInterface.getPublicKey());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    @PostMapping("/normal")
    public ResponseEntity<Result<NormalLoginResponse>> normalLogin(@RequestBody NormalLoginRequest request) {
        return convert(authenInterface.normalLogin(request));
    }
    private <T> ResponseEntity<Result<T>> convert(Result<T> result){
        if(result.isSuccess()){
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(result.getErrorCode()).body(result);
    }
}
