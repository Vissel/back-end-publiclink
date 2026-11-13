package com.qrpublic.apartment.domain.buyerplan;

import com.qrpublic.apartment.adapter.msgbroker.model.PasswordLinkEvent;
import com.qrpublic.apartment.adapter.user.request.UserChangePasswordRequest;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.service.CorePasswordSetupTokenService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.PasswordSetupToken;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.util.DateUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Issues, reports on and redeems the single-use password setup/reset links.
 * The link itself is a plain frontend route plus the DB token code; the real
 * password travels RSA-encrypted to the user service, exactly like the
 * seller register flow.
 */
@Slf4j
@Component
public class PasswordSetupDomain {

    @Autowired
    private CorePasswordSetupTokenService corePasswordSetupTokenService;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private OperatedUserClient operatedUserClient;

    @Autowired
    private com.qrpublic.apartment.integration.PasswordLinkEventPublisher passwordLinkEventPublisher;

    @Value("${app.public.url:https://share-link.top}")
    private String appPublicUrl;

    @Data
    @Builder
    public static class IssuedLink {
        private String tokenCode;
        private String relativeLink;
        private String absoluteLink;
    }

    /**
     * Create a token and queue the broker event that delivers the link
     * (email + in-app copy). Returns the link so every caller can show it
     * copyable even when the broker or SMTP is down.
     */
    @Transactional
    public IssuedLink issueAndNotify(String username, String fullName, String email, String purpose,
            String sellerUsername, String requestedBy) {
        PasswordSetupToken token = corePasswordSetupTokenService.issue(username, purpose, requestedBy);
        String relative = LinkBuilder.buildBuyerSetupPath(token.getTokenCode());

        PasswordLinkEvent event = new PasswordLinkEvent();
        event.setMessageId(UUID.randomUUID().toString());
        event.setUsername(username);
        event.setFullName(fullName);
        event.setEmail(email);
        event.setSetupLink(appPublicUrl + relative);
        event.setPurpose(purpose);
        event.setSellerUsername(sellerUsername);
        event.setRequestedBy(requestedBy);
        passwordLinkEventPublisher.publish(event);

        return IssuedLink.builder()
                .tokenCode(token.getTokenCode())
                .relativeLink(relative)
                .absoluteLink(appPublicUrl + relative)
                .build();
    }

    @Data
    @Builder
    public static class SetupInfo {
        private String username;
        private String fullName;
        private String purpose;
        private boolean valid;
        private String expiresAt;
    }

    @Transactional(readOnly = true)
    public SetupInfo info(String tokenCode) {
        PasswordSetupToken token = corePasswordSetupTokenService.peek(tokenCode);
        com.qrpublic.apartment.core.model.UserModel user = coreUserService.findByUsername(token.getUsername());
        return SetupInfo.builder()
                .username(token.getUsername())
                .fullName(user != null ? user.getName() : token.getUsername())
                .purpose(token.getPurpose())
                .valid(corePasswordSetupTokenService.isValid(token))
                .expiresAt(DateUtils.dateToString(token.getExpiresAt()))
                .build();
    }

    /**
     * Redeem the link: consume the token under lock, then set the real
     * password in the user service and mark the local row authenticated.
     */
    @Transactional
    public void complete(String tokenCode, String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new ApplicationException("A new password is required.", 400);
        }
        PasswordSetupToken token = corePasswordSetupTokenService.consume(tokenCode);

        UserChangePasswordRequest changeRequest = new UserChangePasswordRequest();
        changeRequest.setUserName(token.getUsername());
        changeRequest.setEncryptedPassword(encryptedPassword);
        try {
            var result = operatedUserClient.changePassword(changeRequest)
                    .block(java.time.Duration.ofSeconds(10));
            if (result == null || !result.isSuccess()) {
                // token was consumed for nothing: hand the buyer a fresh link
                throw new ApplicationException(
                        "Could not store the new password. Request a new setup link and try again.", 502);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("User service changePassword failed for [{}]: {}", token.getUsername(), e.getMessage());
            throw new ApplicationException(
                    "Could not reach the account service. Try again later.", 502);
        }

        coreUserService.findUserEntityByUsername(token.getUsername())
                .ifPresent(coreUserService::updateUserWithAuthentication);
        log.info("Password setup completed for [{}] (purpose {})", token.getUsername(), token.getPurpose());
    }
}
