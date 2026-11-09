package com.qrpublic.apartment.chat.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response of the Zalo OAuth v4 token endpoint. Zalo is inconsistent about
 * naming failures ({@code error_code} on the OAuth host, {@code error} on the
 * OpenAPI host) and quotes {@code expires_in}, so both are tolerated here.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZaloTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    /** Seconds until expiry, delivered as a JSON string ("90000" = 25 hours). */
    @JsonProperty("expires_in")
    private String expiresIn;

    @JsonProperty("error_code")
    private Integer errorCode;

    private Integer error;

    private String message;

    public boolean isSuccess() {
        boolean noError = (errorCode == null || errorCode == 0) && (error == null || error == 0);
        return noError && accessToken != null && !accessToken.isBlank();
    }

    /** Lifetime of the access token in milliseconds, defaulting to 25 hours. */
    public long getExpiresInMillis() {
        try {
            long seconds = Long.parseLong(expiresIn.trim());
            return seconds > 0 ? seconds * 1000L : 90_000_000L;
        } catch (RuntimeException e) {
            return 90_000_000L;
        }
    }

    public int resolveErrorCode() {
        if (errorCode != null && errorCode != 0) {
            return errorCode;
        }
        if (error != null && error != 0) {
            return error;
        }
        return -1;
    }
}
