package com.qrpublic.apartment.domain.badminton;

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

/** Token resolution, product-type and lifetime checks for badminton pages. */
@Slf4j
@Component
public class BadmintonEnvAccess {
    @Autowired
    private LinkService linkService;

    @Autowired
    private CoreYogaEnvironmentService coreYogaEnvironmentService;

    @Autowired
    private CoreTimezoneService coreTimezoneService;

    /** Reading the grid tolerates link expiry, matching the yoga read rule. */
    public YogaEnvModel resolveForRead(String token) {
        if (token == null || token.isBlank() || !linkService.validateLinkButNotExpiredValidation(token)) {
            throw new ApplicationException("Invalid link.", 401);
        }
        String requestUuid = (String) linkService.extractClaimByKey(token, LinkConstant.CLAIM_SUBJECT);
        YogaEnvModel env = coreYogaEnvironmentService.resolveByRequestUuid(requestUuid);
        if (!CoreYogaEnvironmentService.TYPE_BADMINTON.equals(env.getProductType())) {
            throw new ApplicationException("This link is not a badminton booking page.", 400);
        }
        return env;
    }

    /** Booking requires a live link and an active, currently open environment. */
    public YogaEnvModel resolveForBooking(String token) {
        if (token == null || token.isBlank() || !linkService.validateLink(token)) {
            throw new ApplicationException("Invalid or expired link.", 401);
        }
        YogaEnvModel env = resolveForRead(token);
        assertAlive(env);
        return env;
    }

    public YogaEnvModel resolveForSeller(String token, String sellerUsername) {
        YogaEnvModel env = resolveForRead(token);
        if (sellerUsername == null || sellerUsername.isBlank()
                || !sellerUsername.equals(env.getSellerUsername())) {
            throw new ApplicationException("This badminton page belongs to another seller.", 403);
        }
        return env;
    }

    public void assertAlive(YogaEnvModel env) {
        if (!env.isActive()) {
            throw new ApplicationException("This product link is no longer active.", 410);
        }
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
