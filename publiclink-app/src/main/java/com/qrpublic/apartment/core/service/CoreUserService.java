package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.coreException.EntityAssert;
import com.qrpublic.apartment.core.model.AuthenticationEnum;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.UserType;
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

    /**
     * Duplicate guard for self-registration: the email lives only in this
     * database (the account service stores no email at all), so the local rows
     * are the directory to compare against. Case-insensitive, because
     * "A@example.com" and "a@example.com" are the same mailbox. A blank email is
     * never "in use" - it is simply absent.
     */
    @Transactional(readOnly = true)
    public boolean isEmailInUse(String email) {
        return email != null && !email.isBlank() && userRepo.existsByEmailIgnoreCase(email.trim());
    }

    /**
     * Same guard for the display name. A blank name falls back to the username
     * at registration, so there is nothing of its own to compare.
     */
    @Transactional(readOnly = true)
    public boolean isNameInUse(String name) {
        return name != null && !name.isBlank() && userRepo.existsByNameIgnoreCase(name.trim());
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
        user.setEmail(sellerDTO.getEmail());
        user.setType(sellerDTO.getUserType().name());
        userRepo.save(user);

        return UserModel.builder()
                .username(user.getUserName())
                .link(user.getLink())
                .name(user.getName())
                .type(user.getType())
                .email(user.getEmail())
                .build();
    }

    /**
     * Local row of a self-registered seller. The account already holds the
     * password the user typed, so it is BASIC from the start; the invite flow
     * keeps using createNewUser, which leaves the method null until the
     * seller finishes the setup link.
     */
    @Transactional
    public UserModel createSelfRegisteredSeller(String username, String name, String email) {
        return createSelfRegistered(username, name, email, UserType.SELLER.name(), null);
    }

    /**
     * Local row of a self-registered account (SELLER or BUYER). The account
     * already holds the password the user typed, so it is BASIC from the start.
     *
     * @param planPurpose which plan group the account is registered to buy from
     */
    @Transactional
    public UserModel createSelfRegistered(String username, String name, String email, String type,
            String planPurpose) {
        User user = new User();
        user.setUserName(username);
        user.setName(name);
        user.setEmail(email);
        user.setType(type);
        user.setPlanPurpose(planPurpose);
        user.setAuthenticationMethod(AuthenticationEnum.BASIC.name());
        userRepo.save(user);

        return UserModel.builder()
                .username(user.getUserName())
                .name(user.getName())
                .type(user.getType())
                .email(user.getEmail())
                .authenticationEnum(AuthenticationEnum.BASIC)
                .build();
    }

    /**
     * Purpose as stored, blank for every account that predates the plan split.
     * Callers resolve it through {@code PlanGroup.fromPurpose}, which reads a
     * blank as PRODUCT so those accounts keep their existing catalog.
     */
    @Transactional(readOnly = true)
    public String findPlanPurpose(String username) {
        return userRepo.findByUserName(username)
                .map(User::getPlanPurpose)
                .orElse(null);
    }

    /**
     * Move an account onto the other plan group. Explicit and rare: it is the
     * way out for someone who picked the wrong purpose at registration, since a
     * conversion never crosses groups.
     */
    @Transactional
    public void updatePlanPurpose(User existingUser, String planPurpose) {
        existingUser.setPlanPurpose(planPurpose);
        userRepo.save(existingUser);
    }

    /**
     * Own-profile edit from the /self page. The display name always changes;
     * the email arrives only while the account has none, which the caller has
     * already asserted. This is the single place those two columns are written,
     * so no other flow can quietly reopen a locked email.
     */
    @Transactional
    public void updateProfile(User existingUser, String name, String email) {
        existingUser.setName(name);
        if (email != null && !email.isBlank()) {
            existingUser.setEmail(email);
        }
        userRepo.save(existingUser);
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
                .email(user.getEmail())
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
    public UserModel findAndUpdateUserWithAuthentication(String username, String password, String email, String name,
            String profileLink) {
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
