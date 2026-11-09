package com.qrpublic.apartment.chat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.chat.integration.model.ZaloApiException;
import com.qrpublic.apartment.chat.integration.model.ZaloTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Zalo OAuth v4 (authorization code + PKCE) for obtaining and refreshing the OA
 * access token that the chat bridge sends messages with.
 *
 * Token lifetimes enforced by Zalo: authorization code 10 minutes single-use,
 * access token 25 hours, refresh token 3 months and rotated on every exchange.
 */
@Slf4j
@Component
public class ZaloAuthClient {

    private static final String TOKEN_PATH = "/v4/oa/access_token";
    private static final String AUTHORIZE_PATH = "/v4/oa/authorize";
    private static final String VERIFIER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final int VERIFIER_LENGTH = 43;

    private final WebClient webClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZaloAuthClient(@Value("${zalo.oauth.base-url:https://oauth.zaloapp.com}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.webClient = WebClient.builder().baseUrl(this.baseUrl).build();
    }

    /**
     * Exchanges the short-lived authorization code returned to the OAuth callback.
     */
    public Mono<ZaloTokenResponse> exchangeAuthorizationCode(String appId, String secretKey, String code,
            String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("app_id", appId);
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);
        return postForToken(form, secretKey);
    }

    /**
     * Rotates an expiring access token. Zalo invalidates the used refresh token.
     */
    public Mono<ZaloTokenResponse> refresh(String appId, String secretKey, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("app_id", appId);
        form.add("grant_type", "refresh_token");
        return postForToken(form, secretKey);
    }

    /**
     * Consent URL the OA administrator must open once to grant the app its scopes.
     */
    public String buildAuthorizeUrl(String appId, String redirectUri, String codeChallenge, String state) {
        return baseUrl + AUTHORIZE_PATH
                + "?grant_type=authorization_code"
                + "&app_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge);
    }

    public static String newCodeVerifier() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(VERIFIER_LENGTH);
        for (int i = 0; i < VERIFIER_LENGTH; i++) {
            builder.append(VERIFIER_ALPHABET.charAt(random.nextInt(VERIFIER_ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * code_challenge = Base64(SHA-256(ASCII(code_verifier))) as specified by Zalo.
     */
    public static String challengeOf(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for Zalo PKCE", e);
        }
    }

    private Mono<ZaloTokenResponse> postForToken(MultiValueMap<String, String> form, String secretKey) {
        return webClient.post()
                .uri(TOKEN_PATH)
                .header("secret_key", secretKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("{}")
                        .map(body -> parse(response.statusCode().value(), body)));
    }

    private ZaloTokenResponse parse(int httpStatus, String body) {
        if (httpStatus >= 400) {
            log.error("Zalo OAuth HTTP {} body: {}", httpStatus, trim(body));
            throw new ZaloApiException("Zalo OAuth returned HTTP " + httpStatus, httpStatus);
        }
        ZaloTokenResponse parsed;
        try {
            parsed = objectMapper.readValue(body, ZaloTokenResponse.class);
        } catch (Exception e) {
            log.error("Unparseable Zalo OAuth body: {}", trim(body), e);
            throw new ZaloApiException("Unparseable response from Zalo OAuth", -1);
        }
        if (!parsed.isSuccess()) {
            log.error("Zalo OAuth refused: {}", trim(body));
            throw ZaloApiException.fromToken(parsed.resolveErrorCode(), parsed.getMessage());
        }
        return parsed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
