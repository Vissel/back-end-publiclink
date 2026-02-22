package com.qrpublic.apartment.authentication.interfaces;

import com.qrpublic.apartment.authentication.interfaces.request.NormalLoginRequest;
import com.qrpublic.apartment.authentication.interfaces.response.NormalLoginResponse;
import com.qrpublic.apartment.template.model.Result;

public interface AuthenInterface {
    /**
     * normal login with username, password
     * @param request
     * @return
     */
    Result<NormalLoginResponse> normalLogin(NormalLoginRequest request);
}
