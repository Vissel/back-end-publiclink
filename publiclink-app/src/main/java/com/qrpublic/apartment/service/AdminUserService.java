package com.qrpublic.apartment.service;

import com.qrpublic.apartment.domain.user.AdminUserDomain;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.user.request.AdminCreateUserRequest;
import com.qrpublic.apartment.user.response.AdminCreateUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Admin user management. Creation lives here rather than in the yoga admin
 * service because it is not yoga-specific: an admin can provision a seller or
 * a buyer for any part of the workspace.
 */
@Slf4j
@Service
public class AdminUserService {

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private AdminUserDomain adminUserDomain;

    public Result<AdminCreateUserResponse> createUser(AdminCreateUserRequest request, String adminUsername) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<AdminCreateUserRequest, AdminCreateUserResponse>() {
                    @Override
                    public AdminCreateUserRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(AdminCreateUserRequest req) {
                        Assert.notNull(req, "Request is required.");
                        Assert.hasText(req.getUsername(), "Username is required.");
                        Assert.hasText(req.getRole(), "Role is required.");
                        Assert.hasText(adminUsername, "Admin identity is required.");
                    }

                    @Override
                    public AdminCreateUserResponse process() {
                        AdminUserDomain.ProvisionedUser created = adminUserDomain.createUser(
                                request.getUsername(), request.getName(), request.getEmail(),
                                request.getRole(), adminUsername);

                        AdminCreateUserResponse response = new AdminCreateUserResponse();
                        response.setUsername(created.getUsername());
                        response.setName(created.getName());
                        response.setEmail(created.getEmail());
                        response.setRole(created.getRole());
                        response.setSetupLink(created.getSetupLink());
                        response.setMessage("Account created. Send the setup link so the user can set a password.");
                        return response;
                    }
                });
    }
}
