package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.core.UserServiceCore;
import com.qrpublic.apartment.user.entity.ProfileEntity;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.exception.UserDeleteException;
import com.qrpublic.apartment.user.exception.UserErrorEnum;
import com.qrpublic.apartment.user.exception.UserNotFoundException;
import com.qrpublic.apartment.user.model.User;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.qrpublic.apartment.adapter.template.Result.success;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    UserServiceCore userServiceCore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RsaClient rsaClient;

    @Override
    public Mono<Result<FoundUserResponse>> findByUserName(String userName) {
        return Mono.fromCallable(() -> {
            User userModel = userServiceCore.doFindUserByUsername(userName);
            if (userModel != null) {
                return Result.<FoundUserResponse>success(mapToFoundUserResponse(userModel));
            }
            return Result.<FoundUserResponse>error(404, "User not found");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Result<UserCreateResponse>> createUser(UserCreateRequest userCreateRequest) {
        return Mono.fromCallable(() -> doCreateUser(userCreateRequest))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Result<UserDeleteResponse>> deleteUserByUsername(UserDeleteRequest userDeleteRequest) {
        return Mono.fromCallable(() -> doDeleteUser(userDeleteRequest))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    protected Result<UserCreateResponse> doCreateUser(UserCreateRequest request) {
        if (request == null) return Result.error(400, "Invalid user request");
        User user = convertUserRegisterToUserModel(request);
        userEntityRepository.findByUsername(user.getUsername())
                .map(existing -> setNewValueForExistUser(existing, user))
                .orElseGet(() -> createNewUser(user));
        return success(buildUserCreateResponse(user));
    }

    @Transactional
    protected Result<UserDeleteResponse> doDeleteUser(UserDeleteRequest request) {
        if (request == null || StringUtils.isBlank(request.getUserId()))
            throw new UserDeleteException(UserErrorEnum.USER_DELETE_ERROR, "invalid");
        User user = new User();
        user.setUserId(request.getUserId());
        user.setUsername(request.getUserName());
        return userEntityRepository.findByUsername(user.getUsername())
                .map(existing -> {
                    userEntityRepository.delete(existing);
                    user.setUsername(existing.getUsername());
                    user.setFullName(existing.getFullName());
                    return success(buildUserDeleteResponse(user));
                })
                .orElseThrow(() -> new UserNotFoundException(user.getUserId()));
    }

    private UserDeleteResponse buildUserDeleteResponse(User user) {
        UserDeleteResponse response = new UserDeleteResponse();
        response.setUserId(user.getUserId());
        response.setDeleted(Boolean.TRUE);
        response.setMessage("User deleted successfully");
        return response;
    }

    private UserCreateResponse buildUserCreateResponse(User user) {
        UserCreateResponse response = new UserCreateResponse();
        response.setUserName(user.getUsername());
        if (StringUtils.isNotBlank(user.getPlainTextPassword())) {
            response.setPassword(PasswordMasker.maskPassword(user.getPlainTextPassword()));
        }
        response.setFullName(user.getFullName());
        if (user.getProfileLinks() != null && !user.getProfileLinks().isEmpty()) {
            response.setLink(user.getProfileLinks().get(0));
        }
        if (Objects.nonNull(user.getRole())) {
            response.setRole(user.getRole());
        }
        return response;
    }

    private User convertUserRegisterToUserModel(UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUserName());
        user.setPlainTextPassword(rsaClient.decrypt(request.getEncryptedPassword()));
        user.setFullName(request.getFullName());
        user.setProfileLinks(List.of(request.getLink()));
        user.setRole(request.getRole());
        user.setIsActive(true);
        return user;
    }

    private FoundUserResponse mapToFoundUserResponse(User userModel) {
        FoundUserResponse response = new FoundUserResponse();
        response.setUserName(userModel.getUsername());
        response.setEncodedPassword(userModel.getPassword());
        response.setName(userModel.getFullName());
        response.setRole(userModel.getRole());
        return response;
    }

    private UserEntity createNewUser(User user) {
        UserEntity newUser = new UserEntity();
        newUser.setUsername(user.getUsername());
        newUser.setPassword(passwordEncoder.encode(user.getPlainTextPassword()));
        newUser.setFullName(user.getFullName());
        newUser.setRole(user.getRole());
        newUser.setIsActive(user.getIsActive());
        if (user.getProfileLinks() != null && !user.getProfileLinks().isEmpty()) {
            List<ProfileEntity> profiles = user.getProfileLinks().stream()
                    .map(link -> {
                        ProfileEntity profile = new ProfileEntity();
                        profile.setProfileLink(link);
                        profile.setProfileType("default");
                        profile.setUser(newUser);
                        return profile;
                    }).toList();
            newUser.setProfiles(profiles);
        }
        return userEntityRepository.save(newUser);
    }

    private UserEntity setNewValueForExistUser(UserEntity existing, User user) {
        Stream.of(
                updateUserName(existing, user.getUsername()),
                updatePassword(existing, user.getPlainTextPassword()),
                updateFullName(existing, user.getFullName()),
                updateRole(existing, user.getRole()),
                updateIsActive(existing, user.getIsActive()),
                updateProfileLinks(existing, user.getProfileLinks())
        ).filter(Objects::nonNull).forEach(updater -> updater.accept(existing));
        return userEntityRepository.save(existing);
    }

    private Consumer<UserEntity> updateUserName(UserEntity u, String v) {
        return StringUtils.isNotBlank(v) && !v.equals(u.getUsername()) ? e -> e.setUsername(v) : null;
    }

    private Consumer<UserEntity> updatePassword(UserEntity u, String v) {
        return StringUtils.isNotBlank(v) && !passwordEncoder.matches(v, u.getPassword()) ? e -> e.setPassword(passwordEncoder.encode(v)) : null;
    }

    private Consumer<UserEntity> updateFullName(UserEntity u, String v) {
        return StringUtils.isNotBlank(v) && !v.equals(u.getFullName()) ? e -> e.setFullName(v) : null;
    }

    private Consumer<UserEntity> updateRole(UserEntity u, String v) {
        return StringUtils.isNotBlank(v) && !v.equals(u.getRole()) ? e -> e.setRole(v) : null;
    }

    private Consumer<UserEntity> updateIsActive(UserEntity u, Boolean v) {
        return v != null && !v.equals(u.getIsActive()) ? e -> e.setIsActive(v) : null;
    }

    private Consumer<UserEntity> updateProfileLinks(UserEntity u, List<String> links) {
        if (links != null && !links.isEmpty()) {
            List<ProfileEntity> profiles = links.stream().map(link -> {
                ProfileEntity p = new ProfileEntity();
                p.setProfileLink(link);
                p.setProfileType("default");
                p.setUser(u);
                return p;
            }).toList();
            u.setProfiles(profiles);
            return e -> {
            };
        }
        return null;
    }
}
