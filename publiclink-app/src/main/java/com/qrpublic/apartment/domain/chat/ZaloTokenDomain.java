package com.qrpublic.apartment.domain.chat;

import com.qrpublic.apartment.core.model.ZaloCredentialModel;
import com.qrpublic.apartment.integration.ZaloAuthClient;
import com.qrpublic.apartment.integration.model.ZaloTokenResponse;
import com.qrpublic.apartment.core.service.CoreZaloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the Zalo OA access token: read-from-store, rotate-through-refresh-token,
 * exchange the OAuth authorization code, and a scheduled pre-expiry refresh.
 *
 * Zalo gives a 25-hour access token and a single-use 3-month refresh token that
 * is invalidated the moment a new one is issued, so exactly one refresh may be in
 * flight per instance — concurrent refreshes would burn a token and leave the
 * persisted one unusable.
 */
@Slf4j
@Component
public class ZaloTokenDomain {

    /** Refresh once the token is closer than this to expiry. */
    @Value("${zalo.token-refresh-skew-millis:600000}")
    private long refreshSkewMillis;

    @Value("${zalo.enabled:false}")
    private boolean enabled;

    @Value("${zalo.app-id:}")
    private String appId;

    @Value("${zalo.secret-key:}")
    private String secretKey;

    /** Optional manual seed for the first deployment, before the OAuth consent runs. */
    @Value("${zalo.seed-access-token:}")
    private String seedAccessToken;

    @Value("${zalo.seed-refresh-token:}")
    private String seedRefreshToken;

    @Value("${zalo.seed-expires-at:0}")
    private long seedExpiresAt;

    /** OAuth callback registered in the Zalo app settings; must match exactly. */
    @Value("${zalo.oauth.redirect-uri:}")
    private String oauthRedirectUri;

    @Autowired
    CoreZaloService coreZaloService;

    @Autowired
    ZaloAuthClient zaloAuthClient;

    private final AtomicReference<Mono<String>> inFlightRefresh = new AtomicReference<>();

    /** Pending consent requests by state, each carrying its PKCE verifier for 10 minutes. */
    private final Map<String, PendingConsent> pendingConsents = new ConcurrentHashMap<>();

    /** Zalo authorization codes expire after 10 minutes. */
    private static final long CONSENT_TTL_MILLIS = 10 * 60 * 1000L;

    /**
     * Starts the one-time OA consent: generates the PKCE pair and returns the URL the
     * OA administrator must open (and approve) in their Zalo account.
     */
    public ConsentStart beginConsent() {
        purgeExpiredConsents();
        String verifier = ZaloAuthClient.newCodeVerifier();
        String state = UUID.randomUUID().toString();
        pendingConsents.put(state, new PendingConsent(verifier, System.currentTimeMillis()));
        String url = zaloAuthClient.buildAuthorizeUrl(appId, oauthRedirectUri,
                ZaloAuthClient.challengeOf(verifier), state);
        return new ConsentStart(url, state);
    }

    /**
     * Completes the consent by trading the returned code for the token pair. The code
     * is single-use, so a second call with the same state cannot succeed.
     */
    public Mono<ZaloCredentialModel> completeConsent(String code, String state) {
        PendingConsent pending = state == null ? null : pendingConsents.remove(state);
        if (pending == null || pending.isExpired()) {
            return Mono.error(new IllegalStateException(
                    "Unknown or expired OAuth state. Restart the Zalo consent flow."));
        }
        if (code == null || code.isBlank()) {
            return Mono.error(new IllegalStateException("Zalo returned no authorization code."));
        }
        return exchangeAuthorizationCode(code, pending.codeVerifier());
    }

    private void purgeExpiredConsents() {
        long now = System.currentTimeMillis();
        pendingConsents.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > CONSENT_TTL_MILLIS);
    }

    /** Start of a consent: the URL to visit plus the state that must come back. */
    public record ConsentStart(String authorizeUrl, String state) {
    }

    private record PendingConsent(String codeVerifier, long startedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - startedAt > CONSENT_TTL_MILLIS;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isBindConfigured() {
        return appId != null && !appId.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    /**
     * A usable OA access token. Never call this on a reactive request thread
     * without the boundedElastic wrapping done here — the store read is JPA.
     */
    public Mono<String> getAccessToken() {
        if (!enabled) {
            return Mono.error(new IllegalStateException("Zalo bridge is disabled (zalo.enabled=false)"));
        }
        return Mono.fromCallable(this::readUsableToken)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(found -> found.map(Mono::just).orElseGet(this::refreshSingleFlight));
    }

    /**
     * Trades the authorization code from the OAuth callback for the token pair.
     * The code is valid for 10 minutes and single-use.
     */
    public Mono<ZaloCredentialModel> exchangeAuthorizationCode(String code, String codeVerifier) {
        return zaloAuthClient.exchangeAuthorizationCode(appId, secretKey, code, codeVerifier)
                .publishOn(Schedulers.boundedElastic())
                .map(response -> coreZaloService.saveToken(response.getAccessToken(),
                        response.getRefreshToken(), System.currentTimeMillis() + response.getExpiresInMillis()));
    }

    /**
     * Hourly safety net. The scheduler renews well before Zalo's 25-hour cut-off
     * so a long idle period never strands the bridge with an expired token.
     */
    @Scheduled(cron = "0 30 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduledRefresh() {
        if (!enabled) {
            return;
        }
        Mono.fromCallable(this::needsRefresh)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(needs -> needs ? refreshSingleFlight().then() : Mono.<Void>empty())
                .doOnError(error -> log.error("Scheduled Zalo token refresh failed: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    private boolean needsRefresh() {
        Optional<ZaloCredentialModel> credential = coreZaloService.findCredential(CoreZaloService.SCOPE_OA_TOKEN);
        if (credential.isEmpty()) {
            return coreZaloService.findCredential(CoreZaloService.SCOPE_ADMIN_USER).isPresent();
        }
        Long expiresAt = credential.get().getExpiresAt();
        return expiresAt == null || expiresAt - System.currentTimeMillis() <= refreshSkewMillis;
    }

    private Optional<String> readUsableToken() {
        Optional<ZaloCredentialModel> stored = coreZaloService.findCredential(CoreZaloService.SCOPE_OA_TOKEN);
        if (stored.isPresent() && isUsable(stored.get())) {
            return Optional.of(stored.get().getValueA());
        }
        if (stored.isEmpty() && seedAccessToken != null && !seedAccessToken.isBlank() && seedExpiresAt > 0) {
            ZaloCredentialModel seeded = coreZaloService.saveToken(seedAccessToken,
                    seedRefreshToken == null || seedRefreshToken.isBlank() ? null : seedRefreshToken,
                    seedExpiresAt);
            return Optional.ofNullable(seeded.getValueA());
        }
        return Optional.empty();
    }

    private boolean isUsable(ZaloCredentialModel credential) {
        return credential.getValueA() != null && !credential.getValueA().isBlank()
                && credential.getExpiresAt() != null
                && credential.getExpiresAt() - System.currentTimeMillis() > refreshSkewMillis;
    }

    /** Shares one refresh across concurrent callers so the rotating token is burned once. */
    private Mono<String> refreshSingleFlight() {
        Mono<String> running = inFlightRefresh.get();
        if (running != null) {
            return running;
        }
        Mono<String> flow = doRefresh()
                .doFinally(signal -> inFlightRefresh.set(null))
                .cache();
        return inFlightRefresh.compareAndSet(null, flow) ? flow : inFlightRefresh.get();
    }

    private Mono<String> doRefresh() {
        return Mono.fromCallable(() -> coreZaloService.findCredential(CoreZaloService.SCOPE_OA_TOKEN)
                        .map(ZaloCredentialModel::getValueB)
                        .orElse(seedRefreshToken))
                .subscribeOn(Schedulers.boundedElastic())
                .filter(refreshToken -> refreshToken != null && !refreshToken.isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No Zalo refresh token available - run the OA OAuth consent once (zalo.oauth.authorize-url)")))
                .flatMap(refreshToken -> zaloAuthClient.refresh(appId, secretKey, refreshToken))
                .publishOn(Schedulers.boundedElastic())
                .map(this::persistAndReturn)
                .doOnNext(token -> log.info("Zalo OA access token refreshed"));
    }

    private String persistAndReturn(ZaloTokenResponse response) {
        coreZaloService.saveToken(response.getAccessToken(), response.getRefreshToken(),
                System.currentTimeMillis() + response.getExpiresInMillis());
        return response.getAccessToken();
    }
}
