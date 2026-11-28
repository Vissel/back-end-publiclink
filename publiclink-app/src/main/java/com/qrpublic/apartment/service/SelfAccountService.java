package com.qrpublic.apartment.service;

import com.qrpublic.apartment.account.request.ChangePasswordRequest;
import com.qrpublic.apartment.account.request.UpdateProfileRequest;
import com.qrpublic.apartment.account.response.BuyerPackageResponse;
import com.qrpublic.apartment.account.response.SelfProfileResponse;
import com.qrpublic.apartment.adapter.user.request.OwnPasswordChangeRequest;
import com.qrpublic.apartment.adapter.user.response.UserChangePasswordResponse;
import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreSellerPaymentPlanService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The account's own page (/self): who I am, and which payment plans pay for
 * what I do. Sellers see the catalog plans they bought, buyers see the packages
 * a seller issued to them. The caller is always the gateway-forwarded JWT
 * identity, never a value from the body, so the page cannot be pointed at
 * another account.
 */
@Slf4j
@Service
public class SelfAccountService {

    /**
     * Same shape as the self-registration and admin screens accept, tightened
     * here with the structural rules that pattern cannot express: no empty
     * label, no label starting or ending with a dot.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final String INVALID_EMAIL_MESSAGE = "Please enter a valid email address.";
    private static final String TOO_LONG_EMAIL_MESSAGE = "The email address must be at most 100 characters.";
    private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoreSellerPaymentPlanService coreSellerPaymentPlanService;

    @Autowired
    private CoreYogaEnvironmentService coreYogaEnvironmentService;

    @Autowired
    private OperatedUserClient operatedUserClient;

    public Result<SelfProfileResponse> profile(String username) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, SelfProfileResponse>() {
            @Override
            public String getRequest() {
                return username;
            }

            @Override
            public void preProcess(String req) {
                Assert.hasText(req, "Account identity is required.");
            }

            @Override
            public SelfProfileResponse process() {
                return toProfile(requireRow(username));
            }
        });
    }

    /**
     * Edit the own profile. The display name is always open; the email is only
     * accepted while the account has none, and then never changes again - the
     * seller packages and setup links are addressed to it.
     */
    public Result<SelfProfileResponse> updateProfile(UpdateProfileRequest request, String username) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<UpdateProfileRequest, SelfProfileResponse>() {
                    @Override
                    public UpdateProfileRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(UpdateProfileRequest req) {
                        Assert.hasText(username, "Account identity is required.");
                        Assert.notNull(req, "Request cannot be null");
                        Assert.hasText(req.getName(), "Full name is required.");
                    }

                    @Override
                    public SelfProfileResponse process() {
                        User user = requireRow(username);
                        String name = request.getName().trim();
                        if (name.length() > MAX_NAME_LENGTH) {
                            throw new ApplicationException(
                                    "The full name must be at most 100 characters.", 400);
                        }

                        String email = request.getEmail() == null ? null : request.getEmail().trim();
                        if (hasText(user.getEmail())) {
                            // Already fixed. Echoing the stored value back is not
                            // an edit, so a form resubmitting it stays harmless.
                            if (hasText(email) && !email.equalsIgnoreCase(user.getEmail())) {
                                throw new ApplicationException(
                                        "The email address cannot change once it is set.", 400);
                            }
                            email = null;
                        } else if (hasText(email)) {
                            email = normalizeEmail(email);
                            if (userRepository.existsByEmailIgnoreCase(email)) {
                                throw new ApplicationException(
                                        "This email address is already used by another account.", 409);
                            }
                        } else {
                            email = null;
                        }

                        coreUserService.updateProfile(user, name, email);
                        log.info("Account [{}] updated own profile (email {})", username,
                                email != null ? "added" : "unchanged");
                        return toProfile(user);
                    }
                });
    }

    /**
     * Change the caller's own password. The username is the JWT-forwarded
     * identity and is never read from the body, and the account service
     * verifies the current password before it writes the new one, so neither a
     * forged request nor a stolen token can take an account over.
     */
    public Result<Void> changePassword(ChangePasswordRequest request, String username) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<ChangePasswordRequest, Void>() {
            @Override
            public ChangePasswordRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChangePasswordRequest req) {
                Assert.hasText(username, "Account identity is required.");
                Assert.notNull(req, "Request cannot be null");
                Assert.hasText(req.getCurrentPassword(), "The current password is required.");
                Assert.hasText(req.getNewPassword(), "A new password is required.");
            }

            @Override
            public Void process() {
                OwnPasswordChangeRequest remote = new OwnPasswordChangeRequest();
                remote.setUserName(username);
                remote.setCurrentEncryptedPassword(request.getCurrentPassword());
                remote.setNewEncryptedPassword(request.getNewPassword());

                com.qrpublic.apartment.adapter.template.Result<UserChangePasswordResponse> answer;
                try {
                    answer = operatedUserClient.changeOwnPassword(remote).block(REMOTE_TIMEOUT);
                } catch (Exception e) {
                    log.error("Account service unreachable for own password change of [{}]: {}",
                            username, e.getMessage());
                    throw new ApplicationException("Could not reach the account service.", 502);
                }
                if (answer == null) {
                    throw new ApplicationException("Could not reach the account service.", 502);
                }
                if (!answer.isSuccess()) {
                    // the reason came from the account service and is written for
                    // the person changing their own password, so it is passed on
                    throw new ApplicationException(
                            answer.getErrorMessage() == null || answer.getErrorMessage().isBlank()
                                    ? "Failed to change the password."
                                    : answer.getErrorMessage(),
                            answer.getErrorCode() > 0 ? answer.getErrorCode() : 400);
                }
                log.info("Account [{}] changed its own password", username);
                return null;
            }
        });
    }

    /** Packages a seller issued to this buyer, each labelled with its page. */
    public Result<List<BuyerPackageResponse>> myPackages(String username) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, List<BuyerPackageResponse>>() {
            @Override
            public String getRequest() {
                return username;
            }

            @Override
            public void preProcess(String req) {
                Assert.hasText(req, "Account identity is required.");
            }

            @Override
            public List<BuyerPackageResponse> process() {
                return coreSellerPaymentPlanService.findActivePlansByBuyer(username).stream()
                        .map(plan -> toPackage(plan, productNameOf(plan.getEnvId())))
                        .toList();
            }
        });
    }

    private BuyerPackageResponse toPackage(SellerPlanModel plan, String productName) {
        return BuyerPackageResponse.builder()
                .sellerPlanCode(plan.getSellerPlanCode())
                .envId(plan.getEnvId())
                .productName(productName)
                .sellerUsername(plan.getSellerUsername())
                .buyerName(plan.getBuyerName())
                .quotaType(plan.getQuotaType())
                .totalDays(plan.getTotalDays())
                .usedDays(plan.getUsedDays())
                .usedBookings(plan.getUsedBookings())
                .remainingDays(plan.getRemainingDays())
                .amountVnd(plan.getAmountVnd())
                .state(plan.getState())
                .build();
    }

    /** Best-effort label: a deleted page must not blank out the package list. */
    private String productNameOf(String envId) {
        try {
            YogaEnvModel env = coreYogaEnvironmentService.resolveByEnvId(envId);
            return env.getProductName();
        } catch (Exception e) {
            log.debug("No product label for env [{}]: {}", envId, e.getMessage());
            return null;
        }
    }

    private User requireRow(String username) {
        return coreUserService.findUserEntityByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + username));
    }

    private SelfProfileResponse toProfile(User user) {
        SelfProfileResponse response = new SelfProfileResponse();
        response.setUsername(user.getUserName());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getType());
        response.setEmailLocked(hasText(user.getEmail()));
        return response;
    }

    /**
     * Shape, structural rules and the stored form of an email, decided in one
     * place so the browser and the server cannot drift on what is acceptable.
     * Lowercased because the duplicate guard compares case-insensitively, and
     * two spellings of one mailbox are one mailbox.
     */
    private static String normalizeEmail(String value) {
        String email = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new ApplicationException(TOO_LONG_EMAIL_MESSAGE, 400);
        }
        // The shape check runs first: it is what guarantees an @ with a non-empty
        // local part, so the index below can never go out of bounds.
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApplicationException(INVALID_EMAIL_MESSAGE, 400);
        }
        if (email.startsWith(".")
                || email.endsWith(".")
                || email.contains("..")
                || email.charAt(email.indexOf('@') - 1) == '.') {
            throw new ApplicationException(INVALID_EMAIL_MESSAGE, 400);
        }
        return email;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
