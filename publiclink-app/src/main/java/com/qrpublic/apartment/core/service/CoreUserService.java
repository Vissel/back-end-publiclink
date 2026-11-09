package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.coreException.EntityAssert;
import com.qrpublic.apartment.core.model.AuthenticationEnum;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CoreUserService {
    @Autowired
    UserRepository userRepo;

    /**
     * Find seller by username and link, if not found, find by username only
     *
     * @param sellerDTO
     * @return
     */
    @Transactional
    public User findSeller(SellerDTO sellerDTO) {
        // Find with username and link
        Optional<User> user = userRepo.findByNameAndLink(sellerDTO.getUsername(), sellerDTO.getLink());
        if (!user.isPresent()) {
            // TODO: will be refactor. find with username
            user = userRepo.findByUserName(sellerDTO.getUsername());
        }
        return user.orElse(null);
    }


    @Transactional
    public List<User> getAllUser() {
        return userRepo.findAllWithLock();
    }

    @Transactional
    public Page<User> getUsers(Pageable pageable) {
        return userRepo.findAll(pageable);
    }

    /**
     * Core-layer entity lookup by username. Used by features that need the FK
     * entity; never exposed above the core layer.
     */
    @Transactional(readOnly = true)
    public User getUserEntityByUsername(String username) {
        return userRepo.findByUserName(username)
                .orElseThrow(() -> new com.qrpublic.apartment.exception.ResourceNotFoundException(
                        "User not found: " + username));
    }

    /**
     * Non-throwing variant: callers such as the transfer info feature must
     * distinguish "known seller" from "admin, who has no row here".
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserEntityByUsername(String username) {
        return userRepo.findByUserName(username);
    }

    @Transactional
    public Page<User> getUsers(Pageable pageable, String username, String name, String email, String role) {
        return userRepo.findByFilters(username, name, email, role, pageable);
    }

    @Transactional
    public UserModel createNewUser(SellerDTO sellerDTO) {
        User user = new User();
        user.setUserName(sellerDTO.getUsername());
        user.setLink(sellerDTO.getLink());
        user.setName(sellerDTO.getName());
        user.setType(sellerDTO.getUserType().name());
        userRepo.save(user);

        return UserModel.builder()
                .username(user.getUserName())
                .link(user.getLink())
                .name(user.getName())
                .type(user.getType())
                .build();
    }

    @Transactional
    public void updateSellerTokenById(String id) {
        User user = userRepo.getUserForUpdate(id);
        EntityAssert.notNull(user, "User is not found");

    }

    @Transactional
    public UserModel findByUsername(String username) {
        return userRepo.findByUserName(username).map(user -> UserModel.builder()
                .username(user.getUserName())
                .link(user.getLink())
                .name(user.getName())
                .type(user.getType())
                .authenticationEnum(AuthenticationEnum.fromString(user.getAuthenticationMethod()))
                .build()).orElse(null);
    }

    @Transactional
    public UserModel updateUserWithAuthentication(User existingUser) {
        // Set authentication method to BASIC
        existingUser.setAuthenticationMethod(AuthenticationEnum.BASIC.name());

        userRepo.save(existingUser);

        return UserModel.builder()
                .username(existingUser.getUserName())
                .link(existingUser.getLink())
                .name(existingUser.getName())
                .type(existingUser.getType())
                .authenticationEnum(AuthenticationEnum.BASIC)
                .build();
    }

    @Transactional
    public UserModel findAndUpdateUserWithAuthentication(String username, String password, String email, String name, String profileLink) {
        // Find user by username
        User existingUser = userRepo.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Update user fields if provided
        if (password != null && !password.trim().isEmpty()) {
            existingUser.setTempPassword(password);
        }
        if (name != null && !name.trim().isEmpty()) {
            existingUser.setName(name);
        }
        if (profileLink != null && !profileLink.trim().isEmpty()) {
            existingUser.setLink(profileLink);
        }
        // Set authentication method to BASIC
        existingUser.setAuthenticationMethod(AuthenticationEnum.BASIC.name());

        userRepo.save(existingUser);

        return UserModel.builder()
                .username(existingUser.getUserName())
                .link(existingUser.getLink())
                .name(existingUser.getName())
                .type(existingUser.getType())
                .authenticationEnum(AuthenticationEnum.BASIC)
                .build();
    }
}
