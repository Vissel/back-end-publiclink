package com.qrpublic.apartment.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.qrpublic.apartment.constant.CommonConstant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LinkUtils {
	private static final String TOKEN_STR = "token=";

	public static String buildPublicLink(String token) {

		String hostBuild = CommonConstant.EMPTY;
		try {
			InetAddress inet = InetAddress.getLocalHost();
			hostBuild += "http://" + inet.getCanonicalHostName() + ":3000";
			log.debug("Current host:{}", inet.getHostName());
			hostBuild += "/public/link?" + TOKEN_STR + token;
		} catch (UnknownHostException e) {
			log.error("Unknowhost:{}", e.getMessage());
		}
		return hostBuild;
	}

	public static String getPublicToken(String link) {
		return link.substring(link.indexOf(TOKEN_STR) + 6);
	}

}
