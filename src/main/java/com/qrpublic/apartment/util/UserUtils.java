package com.qrpublic.apartment.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserUtils {

	public static UserDetails getCurrentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth.getPrincipal() instanceof UserDetails) {
			return (UserDetails) auth.getPrincipal();
		}
		return null;
	}
}
