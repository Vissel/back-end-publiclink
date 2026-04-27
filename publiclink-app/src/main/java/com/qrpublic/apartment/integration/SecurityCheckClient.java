package com.qrpublic.apartment.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "SecurityCheckApplication", url = "http://localhost:8080/security/v1/public")
public interface SecurityCheckClient {
    @PostMapping("/check-token")
    ResponseEntity<Boolean> checkToken(@RequestBody String token);
}
