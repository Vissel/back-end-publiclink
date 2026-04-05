package com.qrpublic.apartment.user.impl;

import com.qrpublic.apartment.authenAuthorisation.authentication.service.RsaService;
import com.qrpublic.apartment.authenAuthorisation.authorisation.model.RoleEnum;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.service.UserService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.ServiceTemplate;
import com.qrpublic.apartment.user.interfaces.request.UserCreateRequest;
import com.qrpublic.apartment.user.interfaces.response.UserCreateResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class ExternalUserServiceImpl {
    @Autowired
    ServiceTemplate template;
    @Autowired
    UserService userService;
    @Autowired
    RsaService rsaService;

    public Result<UserCreateResponse> createUser(UserCreateRequest request) {
        return template.execute(new ProcessCallback<UserCreateRequest, UserCreateResponse>() {
            @Override
            public UserCreateRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(UserCreateRequest request) {
                Assert.isTrue(rsaService.isLikelyRsaEncrypted(request.getEncryptedPassword()), "Password is invalid.");
                Assert.isTrue(RoleEnum.validRole(request.getRole()), "Role is invalid");
            }

            @Override
            public UserCreateResponse process() {
                UserCreateResponse userCreateResponse = new UserCreateResponse();
                RegisterUserDTO userDTO = convertToUserDTO(request);
                userService.saveUser(userDTO);
                userCreateResponse.setMessage("User:" + request.getUserName() + " is created.");
                return userCreateResponse;
            }
        });
    }

    private RegisterUserDTO convertToUserDTO(UserCreateRequest request) {
        RegisterUserDTO dto = new RegisterUserDTO();
        dto.setUserId("0");
        dto.setUserName(request.getUserName());
        dto.setPassword(rsaService.decrypt(request.getEncryptedPassword()));
        dto.setName(request.getFullName());
        dto.setLink(request.getLink());
        dto.setRole(RoleEnum.getRoleEnum(request.getRole()));
        return dto;
    }
}
