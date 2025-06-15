package com.qrpublic.apartment.requestmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuthToken {
	public AuthToken(String token) {
		this.accessToken = token;
	}

	private String accessToken;
	private String refreshToken;
	private String username;
	private String role;

	// Constructors, getters, setters...
}
