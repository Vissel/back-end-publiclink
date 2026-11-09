package com.qrpublic.apartment.service;

import com.qrpublic.apartment.adapter.authentication.response.TokenClaimsResponse;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.integration.SecurityCheckClient;
import com.qrpublic.apartment.service.generating.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RedirectionService {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private LinkService linkService;
    @Autowired
    private CoreRequestService coreRequestService;
    @Autowired
    private JwtService jwtService;

    @Autowired
    UserService userService;

    @Autowired
    private SecurityCheckClient securityCheckClient;

    /**
     * Resolves the redirect destination for a secure link request.
     *
     * @return redirect path, or null if the request is invalid
     */
    public String resolveDestination(String reqUuid, String token, Map<String, String> headers) {
        // Step 1: Validate the link token
        Boolean valid = securityCheckClient.checkToken(token).block();
        if (valid == null || !valid) {
            log.warn("Invalid link token for request: {}", reqUuid);
            return null;
        }

        // Step 2: Extract claims (username, name) from the link token
        TokenClaimsResponse claims = securityCheckClient.extractTokenClaims(token).block();
        if (claims == null || claims.getUsername() == null) {
            log.warn("Failed to extract claims from token for request: {}", reqUuid);
            return null;
        }

        String username = claims.getUsername();
        String name = claims.getName();

        // Step 3: Look up user by username
        if (!userService.checkAuthentedUserExist(username)) {
            // User not found -> redirect to register page with username, reqUuid and name
            log.info("User not found: {}, redirecting to register", username);
            StringBuilder registerUrl = new StringBuilder("/api/v1/publish/register");
            registerUrl.append("?username=").append(username);
            registerUrl.append("&reqUuid=").append(reqUuid);
            if (name != null && !name.isBlank()) {
                registerUrl.append("&name=").append(name);
            }
            return registerUrl.toString();
        }

        // Step 4: User exists -> validate Authorization header
        if (checkValidToken(username, headers)) {
            log.info("User {} authenticated, redirecting to saleUrl", username);
            return "/api/v1/publish/saleUrl?requestUuid=" + reqUuid;
        }

        // Step 5: Auth token invalid or missing -> redirect to login
        log.info("User {} not authenticated, redirecting to login", username);
        return "/login?username=" + username + "&requestUuid=" + reqUuid;
    }

    public boolean checkValidToken(String username, Map<String, String> headers) {
        String authorization = headers != null ? headers.get(AUTH_HEADER) : null;
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String authToken = authorization.substring(BEARER_PREFIX.length());
            Boolean authValid = securityCheckClient.checkToken(authToken).block();
            return authValid != null && authValid && validClaims(username, authToken);
        }
        return false;
    }

    private boolean validClaims(String username, String headerAuthToken) {

        TokenClaimsResponse claims = securityCheckClient.extractTokenClaims(headerAuthToken).block();

        if (claims == null || claims.getUsername() == null) {
            log.warn("Failed to extract claims from header: {}", username);
            return false;
        }
        String claimUsername = claims.getUsername();
        return claimUsername != null && claimUsername.equals(username);
    }
}
