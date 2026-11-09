package com.qrpublic.apartment.chat.service;

import com.qrpublic.apartment.chat.response.ZaloConsentResponse;
import com.qrpublic.apartment.chat.core.model.ZaloCredentialModel;
import com.qrpublic.apartment.chat.core.service.CoreZaloService;
import com.qrpublic.apartment.chat.domain.ZaloTokenDomain;
import com.qrpublic.apartment.chat.template.model.Result;
import com.qrpublic.apartment.chat.template.service.ProcessCallback;
import com.qrpublic.apartment.chat.template.service.ChatServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Operator-facing piece of the Zalo bridge: run the one-time OA consent and
 * read
 * the current connection state. Everything here is administrative, reached
 * through
 * the gateway-protected /admin/v1/chat routes.
 */
@Slf4j
@Service
public class ZaloConnectService {

    @Autowired
    ChatServiceTemplate chatServiceTemplate;

    @Autowired
    ZaloTokenDomain zaloTokenDomain;

    @Autowired
    CoreZaloService coreZaloService;

    @Value("${zalo.oauth.redirect-uri:}")
    private String redirectUri;

    @Value("${zalo.admin-user-id:}")
    private String configuredAdminUserId;

    /** Hand the operator the consent URL for the OA administrator. */
    public Result<ZaloConsentResponse> beginConsent() {
        return chatServiceTemplate.execute(new ProcessCallback<Void, ZaloConsentResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.isTrue(zaloTokenDomain.isBindConfigured(),
                        "zalo.app-id and zalo.secret-key must be configured first.");
                Assert.hasText(redirectUri, "zalo.oauth.redirect-uri must match the URL registered in the Zalo app.");
            }

            @Override
            public ZaloConsentResponse process() {
                ZaloTokenDomain.ConsentStart start = zaloTokenDomain.beginConsent();
                log.info("Zalo OAuth consent started, state={}", start.state());
                return new ZaloConsentResponse(start.authorizeUrl(), start.state(), redirectUri);
            }
        });
    }

    /**
     * Completes the consent. Reactive by design: the token exchange is an outbound
     * call and must never occupy a request thread while Zalo thinks.
     */
    public Mono<Result<String>> completeConsent(String code, String state) {
        return zaloTokenDomain.completeConsent(code, state)
                .publishOn(Schedulers.boundedElastic())
                .map(credential -> message("Zalo OA connected; access token valid until "
                        + java.time.Instant.ofEpochMilli(credential.getExpiresAt() == null
                                ? System.currentTimeMillis()
                                : credential.getExpiresAt())))
                .onErrorResume(error -> {
                    log.error("Zalo OAuth completion failed: {}", error.getMessage());
                    return Mono.just(failure(502, error.getMessage()));
                });
    }

    /** Read-only bridge diagnostics for the console header. */
    public Result<Map<String, Object>> status() {
        return chatServiceTemplate.execute(new ProcessCallback<Void, Map<String, Object>>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // nothing to validate
            }

            @Override
            public Map<String, Object> process() {
                Optional<ZaloCredentialModel> token = coreZaloService.findCredential(CoreZaloService.SCOPE_OA_TOKEN);
                Optional<ZaloCredentialModel> admin = coreZaloService.findCredential(CoreZaloService.SCOPE_ADMIN_USER);

                String adminUserId = firstNonBlank(configuredAdminUserId, admin.map(ZaloCredentialModel::getValueA)
                        .orElse(null));

                Map<String, Object> status = new LinkedHashMap<>();
                status.put("enabled", zaloTokenDomain.isEnabled());
                status.put("tokenPresent", token.isPresent());
                status.put("tokenExpiresAt", token.map(ZaloCredentialModel::getExpiresAt).orElse(null));
                status.put("refreshTokenPresent", token.map(c -> c.getValueB() != null
                        && !c.getValueB().isBlank()).orElse(false));
                status.put("adminBound", adminUserId != null);
                status.put("adminUserId", adminUserId);
                status.put("ready", zaloTokenDomain.isEnabled() && token.isPresent() && adminUserId != null);
                return status;
            }
        });
    }

    private static Result<String> message(String text) {
        Result<String> result = new Result<>();
        result.setSuccess(true);
        result.setData(text);
        result.setErrorCode(200);
        return result;
    }

    private static Result<String> failure(int errorCode, String message) {
        Result<String> result = new Result<>();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(message);
        return result;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }
}
