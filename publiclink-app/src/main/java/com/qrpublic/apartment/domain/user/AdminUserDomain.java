package com.qrpublic.apartment.domain.user;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.user.request.GenerateAccountRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.core.service.CorePasswordSetupTokenService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.buyerplan.PasswordSetupDomain;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.UserType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Admin-side account provisioning: create a seller or a buyer from the
 * workspace, without the admin ever knowing a password.
 *
 * <p>
 * Two systems have to agree on the new account, so the order is the same as
 * self-registration: account service first (generated password, setup link
 * required), then the local business row the foreign keys point at, with the
 * remote account deleted again if the local write fails. The local row stays
 * UNAUTHENTICATED until the new user redeems the setup link, which is what
 * {@code checkAuthentedUserExist} and the buyer flows already read.
 *
 * <p>
 * Runs on the elastic scheduler behind the service template, like
 * {@code adminResetPassword}, so the bounded remote wait is off the event loop.
 */
@Slf4j
@Component
public class AdminUserDomain {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private OperatedUserClient operatedUserClient;

    @Autowired
    private PasswordSetupDomain passwordSetupDomain;

    @Data
    @Builder
    public static class ProvisionedUser {
        private String username;
        private String name;
        private String email;
        private String role;
        private String setupLink;
    }

    @Transactional
    public ProvisionedUser createUser(String rawUsername, String rawName, String rawEmail, String rawRole,
            String adminUsername) {
        String username = trimOf(rawUsername);
        String name = trimOf(rawName);
        String email = trimOf(rawEmail);
        UserType role = parseRole(rawRole);

        validate(username, name, email);
        assertUsernameFree(username);

        GenerateAccountRequest accountRequest = new GenerateAccountRequest();
        accountRequest.setUserName(username);
        accountRequest.setFullName(name);
        accountRequest.setRole(role.name());
        callGenerateAccount(accountRequest, username);

        try {
            SellerDTO dto = new SellerDTO();
            dto.setUsername(username);
            dto.setName(name);
            dto.setLink("");
            dto.setEmail(email.isEmpty() ? null : email);
            dto.setUserType(role);
            coreUserService.createNewUser(dto);
        } catch (RuntimeException e) {
            log.error("Local row failed for admin-created [{}], rolling back the remote account", username, e);
            deleteRemoteAccount(username);
            throw new ApplicationException("The account could not be created. Please try again.", 500);
        }

        // With an email the link is mailed as well; without one the admin still
        // gets it here to forward by hand, and the in-app notification carries it.
        PasswordSetupDomain.IssuedLink link = passwordSetupDomain.issueAndNotify(
                username, name, email.isEmpty() ? null : email,
                CorePasswordSetupTokenService.PURPOSE_SETUP, null, adminUsername);

        log.info("Admin [{}] created {} account [{}]", adminUsername, role, username);
        return ProvisionedUser.builder()
                .username(username)
                .name(name)
                .email(email.isEmpty() ? null : email)
                .role(role.name())
                .setupLink(link.getAbsoluteLink())
                .build();
    }

    private void validate(String username, String name, String email) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApplicationException(
                    "Username must be 3-30 characters: letters, numbers, dot, dash or underscore.", 400);
        }
        if (name.isEmpty()) {
            throw new ApplicationException("The user's name is required.", 400);
        }
        if (name.length() > 100) {
            throw new ApplicationException("Name must be at most 100 characters.", 400);
        }
        if (!email.isEmpty() && (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches())) {
            throw new ApplicationException("Please enter a valid email address, or leave it empty.", 400);
        }
    }

    /**
     * Both stores have to be free: a local row means the name already owns
     * business records, and an account-service row means somebody can already
     * sign in with it.
     */
    private void assertUsernameFree(String username) {
        if (coreUserService.findUserEntityByUsername(username).isPresent()) {
            throw new ApplicationException("This username already exists.", 409);
        }
        try {
            var found = operatedUserClient.findUserByUsername(new FindUserRequest(username))
                    .block(REMOTE_TIMEOUT);
            if (found != null) {
                throw new ApplicationException("This username already exists.", 409);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return; // unknown name: exactly what we want
            }
            log.error("Account service lookup failed for [{}]: {} {}", username, e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new ApplicationException("Could not reach the account service.", 502);
        } catch (Exception e) {
            log.error("Account service unreachable while checking [{}]: {}", username, e.getMessage());
            throw new ApplicationException("Could not reach the account service.", 502);
        }
    }

    private void callGenerateAccount(GenerateAccountRequest request, String username) {
        try {
            var result = operatedUserClient.generateAccount(request).block(REMOTE_TIMEOUT);
            if (result == null || !result.isSuccess()) {
                String message = result != null && result.getErrorMessage() != null
                        ? result.getErrorMessage()
                        : "The account service refused to create the account.";
                throw new ApplicationException(message, 502);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Account service unreachable while creating [{}]: {}", username, e.getMessage());
            throw new ApplicationException("Could not reach the account service.", 502);
        }
    }

    /**
     * Best effort: a failed compensation is logged, the admin still gets a
     * retry-able error.
     */
    private void deleteRemoteAccount(String username) {
        UserRemoveRequest remove = new UserRemoveRequest();
        remove.setUserId(username);
        remove.setUserName(username);
        try {
            operatedUserClient.deleteUser(remove).block(REMOTE_TIMEOUT);
            log.info("Compensation deleted remote account [{}]", username);
        } catch (Exception e) {
            log.error("Compensation failed for [{}]: {}", username, e.getMessage());
        }
    }

    private UserType parseRole(String rawRole) {
        String role = trimOf(rawRole).toUpperCase();
        if (UserType.SELLER.name().equals(role)) {
            return UserType.SELLER;
        }
        if (UserType.BUYER.name().equals(role)) {
            return UserType.BUYER;
        }
        throw new ApplicationException("Role must be SELLER or BUYER.", 400);
    }

    private String trimOf(String value) {
        return value == null ? "" : value.trim();
    }
}
