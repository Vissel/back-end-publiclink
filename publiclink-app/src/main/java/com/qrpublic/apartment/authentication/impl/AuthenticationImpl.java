package com.qrpublic.apartment.authentication.impl;

import com.qrpublic.apartment.authentication.interfaces.AuthenInterface;
import com.qrpublic.apartment.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.authentication.service.RsaService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.ServiceTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationImpl implements AuthenInterface {

    @Autowired
    ServiceTemplate template;

    @Autowired
    AuthenticationManager manager;

    @Autowired
    RsaService rsaService;

    @Override
    public Result<NormalLoginResponse> normalLogin(NormalLoginRequest request) {
        return template.execute(new ProcessCallback<NormalLoginRequest, NormalLoginResponse>() {
            @Override
            public NormalLoginRequest getRequest() {
                return request;
            }
            @Override
            public void preProcess(NormalLoginRequest request) {

            }
            @Override
            public NormalLoginResponse process() {

                Authentication authentication = manager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getUsername(),rsaService.decrypt(request.getEncryptedPassword())));
                UserDetails user = (UserDetails) authentication.getPrincipal();
                String token;

                // For seller
//                if (user.getAuthorities().iterator().next().getAuthority().equals(RoleEnum.SELLER.getRole())) {
//                    token = jwtService.generateTokenByValidTime(user.getUsername(), RoleEnum.SELLER.getRole(),
//                            ACCESS_TOKEN_VALIDITY);
//                } else if (user.getAuthorities().iterator().next().getAuthority().equals(RoleEnum.ADMIN.getRole())) {
//                    token = jwtService.generateToken(user.getUsername(),
//                            user.getAuthorities().iterator().next().getAuthority());
//                } else {
//                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthToken(CommonConstant.EMPTY));
//                }
//
//                AuthToken authRes = new AuthToken(token);
//                authRes.setUsername(user.getUsername());
//                authRes.setRole(user.getAuthorities().iterator().next().getAuthority());
//                SecurityContextHolder.getContext().setAuthentication(authentication);

                return null;
            }
        });
    }
}
