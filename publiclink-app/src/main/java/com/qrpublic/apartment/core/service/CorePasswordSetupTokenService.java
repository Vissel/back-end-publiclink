package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.PasswordSetupToken;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.PasswordSetupTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Core layer for the single-use password setup/reset tokens behind the
 * /buyer-setup link. Validation + consumption happen under a row lock so a
 * link can only be redeemed once.
 */
@Slf4j
@Service
public class CorePasswordSetupTokenService {

    public static final String PURPOSE_SETUP = "SETUP";
    public static final String PURPOSE_RESET = "RESET";

    @Autowired
    private PasswordSetupTokenRepository passwordSetupTokenRepository;

    @Autowired
    private CoreTimezoneService coreTimezoneService;

    @Value("${buyer.setup.link.ttl-hours:48}")
    private long ttlHours;

    @Transactional
    public PasswordSetupToken issue(String username, String purpose, String requestedBy) {
        PasswordSetupToken token = new PasswordSetupToken();
        token.setTokenCode(UUID.randomUUID().toString());
        token.setUsername(username);
        token.setPurpose(purpose);
        token.setRequestedBy(requestedBy);
        token.setExpiresAt(new Timestamp(coreTimezoneService.getNow().getTime() + ttlHours * 3600_000L));
        token.setUsed(false);
        log.info("Issued {} password token for [{}] by [{}]", purpose, username, requestedBy);
        return passwordSetupTokenRepository.save(token);
    }

    /**
     * Read-only peek for the setup page info screen. Reports validity without
     * consuming.
     */
    @Transactional(readOnly = true)
    public PasswordSetupToken peek(String tokenCode) {
        return passwordSetupTokenRepository.findByTokenCode(tokenCode)
                .orElseThrow(() -> new ResourceNotFoundException("Setup link is not valid."));
    }

    /**
     * Lock, assert freshness and single use, then mark consumed. The caller
     * performs the password change after this returns.
     */
    @Transactional
    public PasswordSetupToken consume(String tokenCode) {
        PasswordSetupToken token = passwordSetupTokenRepository.findByTokenCodeForUpdate(tokenCode)
                .orElseThrow(() -> new ResourceNotFoundException("Setup link is not valid."));
        if (token.isUsed()) {
            throw new ApplicationException("This setup link has already been used.", 410);
        }
        if (token.getExpiresAt().getTime() < coreTimezoneService.getNow().getTime()) {
            throw new ApplicationException("This setup link has expired. Ask for a new one.", 410);
        }
        token.setUsed(true);
        return passwordSetupTokenRepository.save(token);
    }

    public boolean isValid(PasswordSetupToken token) {
        return !token.isUsed()
                && token.getExpiresAt().getTime() >= coreTimezoneService.getNow().getTime();
    }
}
