package com.qrpublic.apartment.service;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.service.generating.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class RedirectionService {

    @Autowired
    private LinkService linkService;
    @Autowired
    private CoreRequestService coreRequestService;
    @Autowired
    private JwtService jwtService;

    @Autowired
    UserService userService;

    /**
     * Resolves the redirect destination for a secure link request.
     *
     * @return redirect path, or null if the request is invalid
     */
    public String resolveDestination(String reqId, String token, Map<String, String> headers) {
        if (!linkService.validateLink(token)) {
            return null;
        }
        Optional<Request> requestOpt = coreRequestService.getRequestByUuid(reqId);
        if (requestOpt.isEmpty()) {
            return null;
        }
        // extract username from token by calling linkService.extractClaimByKey(token, "username")
        // call UserService.findUserByUsername(username) to get user object
        // if user is not found, return /api/v1/publish/register
        // else, validate header headers.get("Authorization") beares token. => valid => "/api/v1/publish/saleUrl", invalid => /login

        Request request = requestOpt.get();
        Object usernameObj = linkService.extractClaimByKey(token, LinkConstant.PARAM_USERNAME);
        final String username = (String) usernameObj;

        User user = userService.findByUserName(username);
        if (user == null) {
            return "/api/v1/publish/register";
        }

        String authorization = headers.get("Authorization");
        if (authorization != null && jwtService.isHeaderTokenValid(authorization)) {
            return "/api/v1/publish/saleUrl";
        }
        return "/api/v1/auth/basic";
    }
}
