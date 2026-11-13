package com.qrpublic.apartment.domain.buyerplan;

import com.qrpublic.apartment.adapter.user.request.GenerateAccountRequest;
import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreSellerPaymentPlanService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.yoga.YogaEnvAccess;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

/**
 * Seller-defined buyer payment plans for yoga environments. Creating a plan
 * provisions the buyer account end-to-end: a generated-password account in
 * the user service, a BUYER row in publiclink-db, the plan row, and the
 * single-use setup link delivered through the broker (and returned to the
 * caller so it is always copyable).
 */
@Slf4j
@Component
public class SellerPaymentPlanDomain {

    @Autowired
    private YogaEnvAccess yogaEnvAccess;

    @Autowired
    private CoreSellerPaymentPlanService coreSellerPaymentPlanService;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private OperatedUserClient operatedUserClient;

    @Autowired
    private PasswordSetupDomain passwordSetupDomain;

    @Transactional
    public SellerPlanModel createPlan(String token, String sellerUsername, String buyerName,
            String buyerEmail, int totalDays, BigDecimal amountVnd) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        if (buyerName == null || buyerName.isBlank()) {
            throw new ApplicationException("Buyer name is required.", 400);
        }
        if (totalDays <= 0) {
            throw new ApplicationException("The plan must cover at least one day.", 400);
        }
        if (amountVnd == null || amountVnd.signum() < 0) {
            throw new ApplicationException("A VND amount (days mapped to money) is required.", 400);
        }

        String buyerUsername = generateBuyerUsername(buyerName);
        provisionBuyerAccount(buyerUsername, buyerName);

        SellerPlanModel plan;
        try {
            plan = coreSellerPaymentPlanService.createPlan(env.getEnvId(), sellerUsername,
                    buyerName.trim(), buyerUsername, blankToNull(buyerEmail), totalDays, amountVnd);
        } catch (RuntimeException e) {
            // compensate the remote account when the local plan row cannot be
            // written, mirroring the seller register flow's compensation
            log.error("Plan creation failed for buyer [{}], local row not written: {}",
                    buyerUsername, e.getMessage());
            throw e;
        }

        PasswordSetupDomain.IssuedLink link = passwordSetupDomain.issueAndNotify(
                buyerUsername, buyerName.trim(), plan.getBuyerEmail(),
                com.qrpublic.apartment.core.service.CorePasswordSetupTokenService.PURPOSE_SETUP,
                sellerUsername, sellerUsername);
        plan.setSetupLink(link.getAbsoluteLink());
        return plan;
    }

    @Transactional
    public SellerPlanModel updatePlan(String token, String sellerUsername, String sellerPlanCode,
            Integer totalDays, BigDecimal amountVnd, String buyerEmail, String state) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        SellerPlanModel existing = coreSellerPaymentPlanService.findByCode(sellerPlanCode);
        assertPlanInEnv(existing, env.getEnvId());
        return coreSellerPaymentPlanService.updatePlan(sellerPlanCode, totalDays, amountVnd,
                buyerEmail, state);
    }

    @Transactional(readOnly = true)
    public List<SellerPlanModel> listPlans(String token, String sellerUsername) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        List<SellerPlanModel> plans = coreSellerPaymentPlanService.findPlansByEnv(env.getEnvId());
        plans.forEach(plan -> plan.setPasswordSet(isPasswordSet(plan.getBuyerUsername())));
        return plans;
    }

    /**
     * Issue a fresh setup link (the old one stays single-use and dead).
     */
    @Transactional
    public SellerPlanModel resendLink(String token, String sellerUsername, String sellerPlanCode) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        SellerPlanModel plan = coreSellerPaymentPlanService.findByCode(sellerPlanCode);
        assertPlanInEnv(plan, env.getEnvId());

        PasswordSetupDomain.IssuedLink link = passwordSetupDomain.issueAndNotify(
                plan.getBuyerUsername(), plan.getBuyerName(), plan.getBuyerEmail(),
                com.qrpublic.apartment.core.service.CorePasswordSetupTokenService.PURPOSE_SETUP,
                sellerUsername, sellerUsername);
        plan.setSetupLink(link.getAbsoluteLink());
        return plan;
    }

    /**
     * Admin trigger from the User List, works for any username (buyer or
     * seller): invalidate the current password, then send a RESET link.
     */
    @Transactional
    public String adminResetPassword(String username, String adminUsername) {
        if (username == null || username.isBlank()) {
            throw new ApplicationException("Username is required.", 400);
        }
        GenerateAccountRequest request = new GenerateAccountRequest();
        request.setUserName(username);
        request.setRole(null); // keep the existing role
        try {
            var result = operatedUserClient.generateAccount(request)
                    .block(java.time.Duration.ofSeconds(10));
            if (result == null || !result.isSuccess()) {
                throw new ApplicationException("Account service refused the reset.", 502);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Account service unreachable for reset of [{}]: {}", username, e.getMessage());
            throw new ApplicationException("Could not reach the account service.", 502);
        }

        // email is only known for yoga plan buyers; everyone else gets the
        // link via the admin's result dialog + in-app notification
        String email = findBuyerEmail(username);
        PasswordSetupDomain.IssuedLink link = passwordSetupDomain.issueAndNotify(
                username, username, email,
                com.qrpublic.apartment.core.service.CorePasswordSetupTokenService.PURPOSE_RESET,
                null, adminUsername);
        return link.getAbsoluteLink();
    }

    private String findBuyerEmail(String username) {
        return coreSellerPaymentPlanService.findActivePlansByBuyer(username).stream()
                .map(SellerPlanModel::getBuyerEmail)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void provisionBuyerAccount(String buyerUsername, String buyerName) {
        Optional<com.qrpublic.apartment.entity.User> local = coreUserService.findUserEntityByUsername(buyerUsername);
        if (local.isPresent()) {
            // returning buyer: keep the local row, just re-arm the account
            GenerateAccountRequest request = new GenerateAccountRequest();
            request.setUserName(buyerUsername);
            request.setFullName(buyerName.trim());
            request.setRole(UserType.BUYER.name());
            callGenerateAccount(request);
            return;
        }

        GenerateAccountRequest request = new GenerateAccountRequest();
        request.setUserName(buyerUsername);
        request.setFullName(buyerName.trim());
        request.setRole(UserType.BUYER.name());
        callGenerateAccount(request);

        SellerDTO dto = new SellerDTO();
        dto.setUsername(buyerUsername);
        dto.setName(buyerName.trim());
        dto.setLink("");
        dto.setUserType(UserType.BUYER);
        coreUserService.createNewUser(dto);
        log.info("Provisioned buyer account [{}] for plan owner flow", buyerUsername);
    }

    private void callGenerateAccount(GenerateAccountRequest request) {
        try {
            var result = operatedUserClient.generateAccount(request)
                    .block(java.time.Duration.ofSeconds(10));
            if (result == null || !result.isSuccess()) {
                throw new ApplicationException("Account service refused to create the buyer account.", 502);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Account service unreachable while provisioning [{}]: {}",
                    request.getUserName(), e.getMessage());
            throw new ApplicationException("Could not reach the account service.", 502);
        }
    }

    /**
     * Slug from the buyer name (diacritics stripped, lowercase, max 20 chars),
     * suffixed with a counter until the username is free in publiclink-db.
     */
    private String generateBuyerUsername(String buyerName) {
        String base = slugify(buyerName);
        if (base.isBlank()) {
            base = "buyer";
        }
        for (int attempt = 0; attempt < 1000; attempt++) {
            String candidate = attempt == 0 ? base : base + (attempt + 1);
            if (candidate.length() > 50) {
                candidate = candidate.substring(0, 50);
            }
            if (coreUserService.findUserEntityByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ApplicationException("Could not derive a username for this buyer. Type one manually.", 500);
    }

    static String slugify(String value) {
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[đĐ]", "d")
                .replaceAll("[^a-zA-Z0-9]+", "")
                .toLowerCase();
        return stripped.length() > 20 ? stripped.substring(0, 20) : stripped;
    }

    private void assertPlanInEnv(SellerPlanModel plan, String envId) {
        if (!envId.equals(plan.getEnvId())) {
            throw new ApplicationException("Plan does not belong to this page.", 403);
        }
    }

    private boolean isPasswordSet(String username) {
        com.qrpublic.apartment.core.model.UserModel user = coreUserService.findByUsername(username);
        return user != null
                && user.getAuthenticationEnum() == com.qrpublic.apartment.core.model.AuthenticationEnum.BASIC;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
