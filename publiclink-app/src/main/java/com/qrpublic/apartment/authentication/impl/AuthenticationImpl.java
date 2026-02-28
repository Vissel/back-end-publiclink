package com.qrpublic.apartment.authentication.impl;

import com.qrpublic.apartment.authentication.interfaces.AuthenInterface;
import com.qrpublic.apartment.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.authentication.service.JwtService;
import com.qrpublic.apartment.authentication.service.RsaService;
import com.qrpublic.apartment.requestmodel.RoleEnum;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.ServiceTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class AuthenticationImpl implements AuthenInterface {

    @Autowired
    ServiceTemplate template;

    @Autowired
    AuthenticationManager manager;

    @Autowired
    RsaService rsaService;

    @Autowired
    JwtService jwtService;

    @Override
    public ByteArrayResource getPublicKey() throws IOException {
        return new ByteArrayResource(rsaService.loadPublicKey());
    }

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
                NormalLoginResponse response = new NormalLoginResponse();
                Authentication authentication = manager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getUsername(),
                              rsaService.decrypt(request.getEncryptedPassword())));
                UserDetails user = (UserDetails) authentication.getPrincipal();
                long validTime = getMillisUntilEndOfDay();
                // TODO - currently, for Badminton system only
                String token =
                        jwtService.generateTokenByValidTime(user.getUsername(), RoleEnum.BADMINTON_ADMIN.getRole(), validTime);
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
                SecurityContextHolder.getContext().setAuthentication(authentication);
                response.setMessage("User: " + user.getUsername() + " is authenticated");
                response.setUsername(user.getUsername());
                response.setToken(token);
                response.setAuthenticated(Boolean.TRUE);
                response.setValidTime(new Date(System.currentTimeMillis() + validTime));
                return response;
            }
        });
    }

    private long getMillisUntilEndOfDay() {
        // Get the current time in the system's default time zone
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        // Get the start of the next day (midnight)
        ZonedDateTime endOfDay = now.plusDays(1).truncatedTo(ChronoUnit.DAYS);

        // Calculate the duration between now and the end of the day
        Duration duration = Duration.between(now, endOfDay);

        // Return the duration in milliseconds
        return duration.toMillis();
    }
}
