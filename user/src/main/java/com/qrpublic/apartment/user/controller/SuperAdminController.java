package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.SuperAdminRegisterRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.response.RegisterAdminResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Server-side-only registration of admin users.
 *
 * The payload is username + role only; the service generates a first secure
 * password (BCrypt-stored) and returns it in plaintext exactly once, after
 * which the user is forced through the create-new-password flow on first login.
 *
 * Access is restricted to the server host itself by the nginx allowlist
 * (deploy/nginx/share-link.top.conf) and InternalOnlyWebFilter.
 */
@RestController
@RequestMapping("/api/v1/user/admin/internal")
public class SuperAdminController {

    @Autowired
    UserService userService;

    @PostMapping("/registerUser")
    public Mono<ResponseEntity<Result<UserRegisterResponse>>> registerUser(
            @Valid @RequestBody SuperAdminRegisterRequest request) {
        return userService.registerAdmin(request.getUserName(), request.getRole())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(Result.success(toUserRegisterResponse(result.getData())));
                    }
                    int code = result.getErrorCode() > 0 ? result.getErrorCode() : 400;
                    return ResponseEntity.status(code)
                            .<Result<UserRegisterResponse>>body(Result.error(code, result.getErrorMessage()));
                });
    }

    private UserRegisterResponse toUserRegisterResponse(RegisterAdminResponse data) {
        UserRegisterResponse response = new UserRegisterResponse();
        response.setUserName(data.getUserName());
        response.setPassword(data.getPassword()); // plaintext: returned exactly once
        response.setRole(data.getRole());
        response.setMessage("User registered successfully");
        return response;
    }
}
