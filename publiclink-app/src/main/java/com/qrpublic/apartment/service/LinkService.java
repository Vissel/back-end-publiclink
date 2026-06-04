package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.LinkModel;

import java.util.Map;

public interface LinkService {

    LinkModel generateSecureUrl(String subject, Map<String, String> claims, long expirationMiliSeconds);

    boolean validateLink(String link);

    LinkModel generateAuthLink(Map<String, String> claims);

    Object extractClaimByKey(String token, String key);
}
