package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.LinkModel;

import java.util.Map;

public interface LinkService {

    LinkModel generateSecureUrl(String subject, Map<String, String> claims);

    boolean validateLink(String link);

    LinkModel generateAuthLink(String subject, Map<String, String> claims);

}
