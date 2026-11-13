package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.service.request.ChangePasswordRequest;
import com.qrpublic.apartment.user.service.request.CreateUserAuthRequest;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.request.UserUpdateRequest;
import com.qrpublic.apartment.user.service.response.AvatarResponse;
import com.qrpublic.apartment.user.service.response.ChangePasswordResponse;
import com.qrpublic.apartment.user.service.response.CreateUserAuthResponse;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.RegisterAdminResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
import com.qrpublic.apartment.user.service.response.UserUpdateResponse;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<Result<FoundUserResponse>> findByUserName(String userName);

    Mono<Result<UserCreateResponse>> createUser(UserCreateRequest userCreateRequest);

    Mono<Result<UserUpdateResponse>> updateUser(UserUpdateRequest userUpdateRequest);

    Mono<Result<UserDeleteResponse>> deleteUserByUsername(UserDeleteRequest userRemoveRequest);

    Mono<Result<CreateUserAuthResponse>> createUserAuth(CreateUserAuthRequest createUserAuthRequest);

    Mono<Result<RegisterAdminResponse>> registerAdmin(String userName, String role);

    Mono<Result<RegisterAdminResponse>> resetChangeTicket(String userName);

    /**
     * Create an account with a generated first password (never returned) or
     * reset an existing one the same way and re-arm must-change-password.
     * The real password is set later via the single-use setup link.
     */
    Mono<Result<UserCreateResponse>> generateAccount(String userName, String fullName, String role);

    Mono<Result<ChangePasswordResponse>> changePassword(ChangePasswordRequest changePasswordRequest);

    /** Own profile picture; a success with a null dataUrl means "none uploaded". */
    Mono<Result<AvatarResponse>> findAvatar(String userName);

    /** Replace, or with a blank dataUrl delete, the own profile picture. */
    Mono<Result<AvatarResponse>> saveAvatar(String userName, String dataUrl);

}
