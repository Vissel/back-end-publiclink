package com.qrpublic.apartment.authentication.interfaces;

import com.qrpublic.apartment.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.template.model.Result;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;

public interface AuthenInterface {
    ByteArrayResource getPublicKey() throws IOException;
    /**
     * normal login with username, password
     * @param request
     * @return
     */
    Result<NormalLoginResponse> normalLogin(NormalLoginRequest request);
}
