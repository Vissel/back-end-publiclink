package com.qrpublic.apartment.user.controller;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.service.UserService;
import com.qrpublic.apartment.user.service.request.AvatarUpdateRequest;
import com.qrpublic.apartment.user.service.response.AvatarResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Own profile picture, used by the header account menu.
 *
 * Identity is taken from X-User-ID, which the gateway now overwrites from the
 * validated JWT (inbound values are stripped first), so it cannot be forged.
 * The gateway also requires authentication on this exact path: the rest of
 * /api/v1/user/** is still permit-all for the server-to-server calls, which is
 * why the identity check is repeated here for the blank-header case.
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserAvatarController {

    private static final String USER_ID_HEADER = "X-User-ID";

    @Autowired
    UserService userService;

    @GetMapping("/avatar")
    public Mono<ResponseEntity<Result<AvatarResponse>>> getAvatar(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userName) {
        return userService.findAvatar(userName).map(this::toResponse);
    }

    @PutMapping("/avatar")
    public Mono<ResponseEntity<Result<AvatarResponse>>> putAvatar(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userName,
            @RequestBody(required = false) AvatarUpdateRequest request) {
        return userService
                .saveAvatar(userName, request == null ? null : request.getDataUrl())
                .map(this::toResponse);
    }

    private ResponseEntity<Result<AvatarResponse>> toResponse(Result<AvatarResponse> result) {
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        int code = result.getErrorCode() > 0 ? result.getErrorCode() : 400;
        return ResponseEntity.status(code).body(Result.error(code, result.getErrorMessage()));
    }
}
