package com.qrpublic.apartment.domain.yoga;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Shared token -> environment resolution and lifetime enforcement for the
 * yoga feature. Reading the calendar tolerates an expired link (sellers
 * legitimately review past environments, same rule as the plain sale space);
 * booking asserts the link is valid AND the environment still alive, matching
 * OrderImpl's enforcement (state + precise willEndedAt).
 */
@Slf4j
@Component
public class YogaEnvAccess {

    @Autowired
    private LinkService linkService;

    @Autowired
    private CoreYogaEnvironmentService coreYogaEnvironmentService;

    @Autowired
    private CoreTimezoneService coreTimezoneService;

    /** Read access: signature-valid token only, expiry tolerated on purpose. */
    public YogaEnvModel resolveForRead(String token) {
        if (token == null || token.isBlank() || !linkService.validateLinkButNotExpiredValidation(token)) {
            throw new ApplicationException("Invalid link.", 401);
        }
        String requestUuid = (String) linkService.extractClaimByKey(token, LinkConstant.CLAIM_SUBJECT);
        YogaEnvModel env = coreYogaEnvironmentService.resolveByRequestUuid(requestUuid);
        if (!CoreYogaEnvironmentService.TYPE_YOGA.equals(env.getProductType())) {
            throw new ApplicationException("This link is not a yoga booking page.", 400);
        }
        return env;
    }

    /**
     * Write access (booking): live link + active environment + not past deadline.
     */
    public YogaEnvModel resolveForBooking(String token) {
        if (token == null || token.isBlank() || !linkService.validateLink(token)) {
            throw new ApplicationException("Invalid or expired link.", 401);
        }
        YogaEnvModel env = resolveForRead(token);
        assertAlive(env);
        return env;
    }

    /** Seller management access: caller must own the environment. */
    public YogaEnvModel resolveForSeller(String token, String sellerUsername) {
        YogaEnvModel env = resolveForRead(token);
        if (sellerUsername == null || sellerUsername.isBlank()
                || !sellerUsername.equals(env.getSellerUsername())) {
            throw new ApplicationException("This yoga page belongs to another seller.", 403);
        }
        return env;
    }

    public void assertAlive(YogaEnvModel env) {
        if (!env.isActive()) {
            throw new ApplicationException("This product link is no longer active.", 410);
        }
        // Same window rule as OrderImpl: a sale that has not opened yet takes no
        // bookings, and one the seller closed early stops at willEndedAt rather
        // than waiting for the hourly job to flip state.
        if (env.getWillStartedAtMillis() != null && env.getWillStartedAtMillis() > System.currentTimeMillis()) {
            throw new ApplicationException("This product link has not started yet.", 410);
        }
        if (env.getWillEndedAtMillis() != null && env.getWillEndedAtMillis() < System.currentTimeMillis()) {
            throw new ApplicationException("This product link has ended.", 410);
        }
    }

    public LocalDate dbToday() {
        return toDbZone(coreTimezoneService.getNow()).toLocalDate();
    }

    public LocalTime dbNowTime() {
        return toDbZone(coreTimezoneService.getNow()).toLocalTime();
    }

    private java.time.ZonedDateTime toDbZone(Date now) {
        return now.toInstant().atZone(ZoneId.of(coreTimezoneService.getDatabaseTimeZone().getID()));
    }
}
