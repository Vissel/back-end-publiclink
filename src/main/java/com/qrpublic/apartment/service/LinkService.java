package com.qrpublic.apartment.service;

public interface LinkService {

	public String generateSecureUrl(String idString, long reqId);

	public boolean validateLink(String link);

}
