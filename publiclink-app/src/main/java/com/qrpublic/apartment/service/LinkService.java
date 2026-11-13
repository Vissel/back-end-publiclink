package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.LinkModel;

import java.util.Date;
import java.util.Map;

public interface LinkService {

    LinkModel generateSecureUrl(String subject, Map<String, String> claims, long expirationMiliSeconds);

    /**
     * Generate a token that expires at an exact instant. Used where the
     * lifetime is capped by a business deadline (e.g. the paid plan end date):
     * unlike {@link #generateSecureUrl}, no end-of-day padding is added.
     */
    LinkModel generateSecureUrlUntil(String subject, Map<String, String> claims, Date expiration);

    boolean validateLink(String link);

    boolean validateLinkButNotExpiredValidation(String publicToken);

    LinkModel generateAuthLink(Map<String, String> claims);

    Object extractClaimByKey(String token, String key);
}
