package com.qrpublic.apartment.service;

import com.qrpublic.apartment.domain.buyerplan.SellerPaymentPlanDomain;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.yoga.request.ResetPasswordRequest;
import com.qrpublic.apartment.yoga.response.ResetPasswordResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Admin yoga-adjacent operations. Reset-password works for any username
 * (buyer or seller); the domain invalidates the current password and returns
 * the fresh single-use setup link for the admin to forward.
 */
@Slf4j
@Service
public class YogaAdminService {

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private SellerPaymentPlanDomain sellerPaymentPlanDomain;

    public Result<ResetPasswordResponse> resetPassword(ResetPasswordRequest request, String adminUsername) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<ResetPasswordRequest, ResetPasswordResponse>() {
                    @Override
                    public ResetPasswordRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(ResetPasswordRequest req) {
                        Assert.notNull(req, "Request is required.");
                        Assert.hasText(req.getUsername(), "Username is required.");
                    }

                    @Override
                    public ResetPasswordResponse process() {
                        String username = request.getUsername().trim();
                        String link = sellerPaymentPlanDomain.adminResetPassword(username, adminUsername);
                        ResetPasswordResponse response = new ResetPasswordResponse();
                        response.setUsername(username);
                        response.setSetupLink(link);
                        return response;
                    }
                });
    }
}
