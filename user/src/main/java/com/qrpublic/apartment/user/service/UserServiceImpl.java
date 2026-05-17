package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.core.UserServiceCore;
import com.qrpublic.apartment.user.entity.ProfileEntity;
import com.qrpublic.apartment.user.entity.UserAuthEntity;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.exception.UserDeleteException;
import com.qrpublic.apartment.user.exception.UserErrorEnum;
import com.qrpublic.apartment.user.exception.UserNotFoundException;
import com.qrpublic.apartment.user.model.User;
import com.qrpublic.apartment.user.model.UserType;
import com.qrpublic.apartment.user.repository.UserAuthEntityRepository;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.request.CreateUserAuthRequest;
import com.qrpublic.apartment.user.service.request.UserCreateRequest;
import com.qrpublic.apartment.user.service.request.UserDeleteRequest;
import com.qrpublic.apartment.user.service.response.CreateUserAuthResponse;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import com.qrpublic.apartment.user.service.response.UserDeleteResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.qrpublic.apartment.adapter.template.Result.success;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private UserAuthEntityRepository userAuthEntityRepository;

    @Autowired
    UserServiceCore userServiceCore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RsaClient rsaClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final TransactionTemplate transactionTemplate;

    public UserServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

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
        return Mono.fromCallable(() ->
                        transactionTemplate.execute(status ->
                                doCreateUser(userCreateRequest))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Result<UserDeleteResponse>> deleteUserByUsername(UserDeleteRequest userDeleteRequest) {
        return Mono.fromCallable(() ->
                        transactionTemplate.execute(status ->
                                doDeleteUser(userDeleteRequest))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Result<CreateUserAuthResponse>> createUserAuth(CreateUserAuthRequest createUserAuthRequest) {
        return Mono.fromCallable(() ->
                        transactionTemplate.execute((status) ->
                                doCreateUserAuth(createUserAuthRequest)
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(ex -> log.error("Failed to create user auth for {}", createUserAuthRequest.getUserName(), ex))
                .onErrorReturn(Result.error(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode(), UserErrorEnum.USER_CREATE_AUTH_ERROR.getMessage()));

    }


    protected Result<CreateUserAuthResponse> doCreateUserAuth(CreateUserAuthRequest request) {
        UserEntity user = userEntityRepository.findByUsername(request.getUserName())
                .orElseGet(() -> {
                    User userModel = new User();
                    userModel.setUsername(request.getUserName());
                    userModel.setRole(UserType.SELLER.name());
                    userModel.setIsActive(true);
                    return createNewUser(userModel);
                });

        UserAuthEntity auth = new UserAuthEntity();
        auth.setUser(user);
        auth.setAuthToken(request.getAuthToken());
        auth.setExpireAt(new java.sql.Timestamp(request.getExpire().getTime()));
        auth.setIsActive(true);
        userAuthEntityRepository.save(auth);

        CreateUserAuthResponse response = new CreateUserAuthResponse();
        response.setSuccess(true);
        return success(response);
    }

    protected Result<UserCreateResponse> doCreateUser(UserCreateRequest request) {
        if (request == null) return Result.error(400, "Invalid user request");
        User user = convertUserRegisterToUserModel(request);
        userEntityRepository.findByUsername(user.getUsername())
                .map(existing -> setNewValueForExistUser(existing, user))
                .orElseGet(() -> createNewUser(user));
        return success(buildUserCreateResponse(user));
    }

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
        newUser.setUserId(UUID.randomUUID().toString());
        newUser.setUsername(user.getUsername());
        if (StringUtils.isNotBlank(user.getPlainTextPassword())) {
            newUser.setPassword(passwordEncoder.encode(user.getPlainTextPassword()));
        }
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
