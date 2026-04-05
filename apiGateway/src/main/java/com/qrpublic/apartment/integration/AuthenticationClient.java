package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.authentication.request.NormalLoginRequest;
import com.qrpublic.apartment.adapter.authentication.response.NormalLoginResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "authen-authorisation", url = "https://localhost:8081/api/v1/server-auth")
public interface AuthenticationClient {

    @PostMapping("/normal")
    ResponseEntity<NormalLoginResponse> normalLogin(@RequestBody NormalLoginRequest request);

    @GetMapping("/public-key")
    ResponseEntity<ByteArrayResource> getPublicKey();
}
