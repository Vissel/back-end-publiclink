package com.qrpublic.apartment.integration.authentication;

import com.qrpublic.apartment.authentication.interfaces.AuthenInterface;
import com.qrpublic.apartment.integration.authentication.request.NormalLoginRequest;
import com.qrpublic.apartment.integration.authentication.response.NormalLoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("authenticationServer")
public class AuthenticationServer {
    @Autowired
    AuthenInterface authenInterface;

    public NormalLoginResponse normalLogin(NormalLoginRequest request){

        return
                authenInterface.normalLogin(request);
    }
}
