package com.qrpublic.apartment.authenAuthorisation.authentication.interfaces;

import com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authenAuthorisation.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.authenAuthorisation.template.model.Result;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;

public interface AuthenInterface {
    ByteArrayResource getPublicKey() throws IOException;

    /**
     * normal login with username, password
     *
     * @param request
     * @return
     */
    Result<NormalLoginResponse> normalLogin(NormalLoginRequest request);
}
