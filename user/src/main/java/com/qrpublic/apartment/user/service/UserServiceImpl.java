package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.model.User;
import com.qrpublic.apartment.user.model.UserRole;
import com.qrpublic.apartment.user.model.UserType;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Stream;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RsaClient rsaClient;

    @Autowired
    private PasswordMasker passwordMasker;

    @Override
    public Result<FoundUserResponse> findByUserName(String userName) {
        return userEntityRepository.findByUserName(userName)
                .map(userEntity -> Result.success(mapToFoundUserResponse(userEntity)))
                .orElseGet(() -> Result.error(404, "User not found"));
    }

    @Override
    public Result<UserCreateResponse> createUser(UserRegisterRequest userCreateRequest) {
        return Stream.of(userCreateRequest)
                .filter(request -> Objects.nonNull(request))
                .map(request -> convertToUserModel(request))
                .map(user -> new java.util.AbstractMap.SimpleEntry<>(user,
                        userEntityRepository.findByUserName(user.getUserName())
                                .map(existingUser -> setNewValueForExistUser(existingUser, user))
                                .orElseGet(() -> createNewUser(user))))
                .map(entry -> {
                    User user = entry.getKey();
                    UserCreateResponse response = buildUserCreateResponse(user);
                    return Result.success(response);
                })
                .findFirst()
                .orElseGet(() -> Result.error(400, "Invalid user request"));
    }

    /**
     * Builds UserCreateResponse from User model
     */
    private UserCreateResponse buildUserCreateResponse(User user) {
        UserCreateResponse response = new UserCreateResponse();
        response.setUserName(user.getUserName());
        if (StringUtils.isNotBlank(user.getPlainTextPassword())) {
            response.setPassword(passwordMasker.maskPassword(user.getPlainTextPassword())); // Masked password for security
        }
        response.setFullName(user.getFullName());
        response.setLink(user.getProfileLink());
        if (Objects.nonNull(user.getUserRole())) {
            response.setRole(user.getUserRole().name());
        }
        if (Objects.nonNull(user.getUserType())) {
            response.setType(user.getUserType().name());
        }
        return response;
    }

    private User convertToUserModel(UserRegisterRequest request) {
        User user = new User();
        user.setUserName(request.getUserName());
        user.setPlainTextPassword(rsaClient.decrypt(request.getEncryptedPassword()));
        user.setFullName(request.getFullName());
        user.setProfileLink(request.getLink());
        user.setUserRole(UserRole.toUserRole(request.getRole()));
        user.setUserType(UserType.toUserType(request.getUserType()));
        return user;
    }

    /**
     * Maps UserEntity to FoundUserResponse
     */
    private FoundUserResponse mapToFoundUserResponse(UserEntity userEntity) {
        FoundUserResponse response = new FoundUserResponse();
        response.setUserName(userEntity.getUserName());
        response.setEncodedPassword(userEntity.getTempPassword());
        response.setName(userEntity.getName());
        return response;
    }

    /**
     * Validates user request with stream processing
     */
    private boolean isValidRequest(UserRegisterRequest request) {
        return Stream.of(
                request.getUserName(),
                request.getEncryptedPassword(),
                request.getFullName()
        ).allMatch(Objects::nonNull);
    }

    /**
     * Creates a new user entity from the registration request
     */
    private UserEntity createNewUser(User user) {
        UserEntity newUser = new UserEntity(
                user.getUserName(),
                passwordEncoder.encode(user.getPlainTextPassword()),
                user.getFullName(),
                user.getProfileLink(),
                user.getUserType().name()
        );
        return userEntityRepository.save(newUser);
    }

    /**
     * Updates existing user with new data using stream processing
     */
    private UserEntity setNewValueForExistUser(UserEntity existingUser, User user) {
        Stream.of(
                        updateUserName(existingUser, user.getUserName()),
                        updatePassword(existingUser, user.getPlainTextPassword()),
                        updateFullName(existingUser, user.getFullName()),
                        updateLink(existingUser, user.getProfileLink())
                ).filter(Objects::nonNull)
                .forEach(updater -> updater.accept(existingUser));

        return userEntityRepository.save(existingUser);
    }

    /**
     * Stream-based field update utilities
     */
    private java.util.function.Consumer<UserEntity> updateUserName(UserEntity user, String newUserName) {
        return StringUtils.isNotBlank(newUserName) && !newUserName.equals(user.getUserName())
                ? u -> u.setUserName(newUserName)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updatePassword(UserEntity user, String newRawPassword) {
        return StringUtils.isNotBlank(newRawPassword) && !passwordEncoder.matches(newRawPassword, user.getTempPassword())
                ? u -> u.setTempPassword(passwordEncoder.encode(newRawPassword))
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateFullName(UserEntity user, String newName) {
        return StringUtils.isNotBlank(newName) && !newName.equals(user.getName())
                ? u -> u.setName(newName)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateLink(UserEntity user, String newLink) {
        return StringUtils.isNotBlank(newLink) && !newLink.equals(user.getLink())
                ? u -> u.setLink(newLink)
                : null;
    }
}
