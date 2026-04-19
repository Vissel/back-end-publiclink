package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.user.entity.ProfileEntity;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.model.User;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import com.qrpublic.apartment.user.service.response.UserCreateResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return userEntityRepository.findByUsername(userName)
                .map(userEntity -> Result.success(mapToFoundUserResponse(userEntity)))
                .orElseGet(() -> Result.error(404, "User not found"));
    }

    @Override
    public Result<UserCreateResponse> createUser(UserRegisterRequest userCreateRequest) {
        return Stream.of(userCreateRequest)
                .filter(Objects::nonNull)
                .map(this::convertToUserModel)
                .map(user -> new java.util.AbstractMap.SimpleEntry<>(user,
                        userEntityRepository.findByUsername(user.getUsername())
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
        response.setUserName(user.getUsername());
        if (StringUtils.isNotBlank(user.getPlainTextPassword())) {
            response.setPassword(PasswordMasker.maskPassword(user.getPlainTextPassword())); // Masked password for security
        }
        response.setFullName(user.getFullName());
        if (user.getProfileLinks() != null && !user.getProfileLinks().isEmpty()) {
            response.setLink(user.getProfileLinks().get(0)); // Get first profile link
        }
        if (Objects.nonNull(user.getRole())) {
            response.setRole(user.getRole());
        }
        return response;
    }

    private User convertToUserModel(UserRegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUserName());
        user.setPlainTextPassword(rsaClient.decrypt(request.getEncryptedPassword()));
        user.setFullName(request.getFullName());
        if (request.getProfileLink() != null) {
            user.setProfileLinks(List.of(request.getProfileLink().getLink()));
        }
        user.setRole(request.getRole());
        user.setIsActive(true);
        return user;
    }

    /**
     * Maps UserEntity to FoundUserResponse
     */
    private FoundUserResponse mapToFoundUserResponse(UserEntity userEntity) {
        FoundUserResponse response = new FoundUserResponse();
        response.setUserName(userEntity.getUsername());
        response.setEncodedPassword(userEntity.getPassword());
        response.setName(userEntity.getFullName());
        response.setRole(userEntity.getRole());
        return response;
    }

    /**
     * Creates a new user entity from the registration request
     */
    private UserEntity createNewUser(User user) {
        UserEntity newUser = new UserEntity();
        newUser.setUsername(user.getUsername());
        newUser.setPassword(passwordEncoder.encode(user.getPlainTextPassword()));
        newUser.setFullName(user.getFullName());
        newUser.setRole(user.getRole());
        newUser.setIsActive(user.getIsActive());

        // Handle profile links if present
        if (user.getProfileLinks() != null && !user.getProfileLinks().isEmpty()) {
            List<ProfileEntity> profiles = user.getProfileLinks().stream()
                    .map(link -> {
                        ProfileEntity profile = new ProfileEntity();
                        profile.setProfileLink(link);
                        profile.setProfileType("default");
                        profile.setUser(newUser);
                        return profile;
                    })
                    .toList();
            newUser.setProfiles(profiles);
        }

        return userEntityRepository.save(newUser);
    }

    /**
     * Updates existing user with new data using stream processing
     */
    private UserEntity setNewValueForExistUser(UserEntity existingUser, User user) {
        Stream.of(
                        updateUserName(existingUser, user.getUsername()),
                        updatePassword(existingUser, user.getPlainTextPassword()),
                        updateFullName(existingUser, user.getFullName()),
                        updateRole(existingUser, user.getRole()),
                        updateIsActive(existingUser, user.getIsActive()),
                        updateProfileLinks(existingUser, user.getProfileLinks())
                ).filter(Objects::nonNull)
                .forEach(updater -> updater.accept(existingUser));

        return userEntityRepository.save(existingUser);
    }

    /**
     * Stream-based field update utilities
     */
    private java.util.function.Consumer<UserEntity> updateUserName(UserEntity user, String newUserName) {
        return StringUtils.isNotBlank(newUserName) && !newUserName.equals(user.getUsername())
                ? u -> u.setUsername(newUserName)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updatePassword(UserEntity user, String newRawPassword) {
        return StringUtils.isNotBlank(newRawPassword) && !passwordEncoder.matches(newRawPassword, user.getPassword())
                ? u -> u.setPassword(passwordEncoder.encode(newRawPassword))
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateFullName(UserEntity user, String newName) {
        return StringUtils.isNotBlank(newName) && !newName.equals(user.getFullName())
                ? u -> u.setFullName(newName)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateRole(UserEntity user, String newRole) {
        return StringUtils.isNotBlank(newRole) && !newRole.equals(user.getRole())
                ? u -> u.setRole(newRole)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateIsActive(UserEntity user, Boolean newIsActive) {
        return newIsActive != null && !newIsActive.equals(user.getIsActive())
                ? u -> u.setIsActive(newIsActive)
                : null;
    }

    private java.util.function.Consumer<UserEntity> updateProfileLinks(UserEntity user, List<String> newProfileLinks) {
        if (newProfileLinks != null && !newProfileLinks.isEmpty()) {
            // Update or set new profile links
            List<ProfileEntity> updatedProfiles = newProfileLinks.stream()
                    .map(link -> {
                        ProfileEntity profile = new ProfileEntity();
                        profile.setProfileLink(link);
                        profile.setProfileType("default");
                        profile.setUser(user);
                        return profile;
                    })
                    .toList();
            user.setProfiles(updatedProfiles);
            return u -> {
            }; // No-op since we already updated
        }
        return null;
    }


}
