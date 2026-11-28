package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.service.CoreAuthorService;
import com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the human identity of a comment author: the display name shown on
 * the comment (and in the avatar hover tooltip) and the profile picture.
 *
 * Deliberately best effort, exactly like chat-app's ChatAccountDomain: the
 * authoritative identity is the username the gateway forwards in X-User-ID, and
 * a slow or down profile service must never stop a comment from being posted,
 * so every lookup has a short timeout and a fallback:
 *
 * - Display name: publiclink-app /api/v1/account/self (the business row
 *   publiclink-db.user.name, the single display-name source of this
 *   application), falling back to the user service's full_name, then username.
 *   Admins have no business row, which is why the fallback exists.
 * - Avatar: the user service GET /api/v1/user/avatar, called server-to-server
 *   with the forwarded identity header. The public avatar endpoint of this
 *   service reads the cached copy below, so no new public user-service route
 *   is ever needed.
 *
 * Called from the service layer's bounded-elastic workers, never from the event
 * loop, so the bounded blocks below are safe.
 */
@Slf4j
@Component
public class AuthorProfileDomain {

    private static final String USER_ID_HEADER = "X-User-ID";

    private final WebClient publiclinkClient;
    private final WebClient userClient;

    @Value("${comment.avatar-cache-minutes:60}")
    private long cacheMinutes;

    @Autowired
    private CoreAuthorService coreAuthorService;

    public AuthorProfileDomain(
            @Value("${comment.publiclink-service-url:http://127.0.0.1:9080}") String publiclinkServiceUrl,
            @Value("${comment.user-service-url:http://127.0.0.1:8082}") String userServiceUrl) {
        this.publiclinkClient = WebClient.builder().baseUrl(publiclinkServiceUrl).build();
        this.userClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    /**
     * Snapshot written onto a new comment: name resolved (username fallback),
     * avatar refreshed into the cache when it is older than the configured TTL
     * or was never fetched.
     */
    public AuthorSnapshot resolveSnapshot(String username) {
        Optional<CommentAuthorAvatarEntity> cached = coreAuthorService.findAuthor(username);
        String displayName = cached.map(CommentAuthorAvatarEntity::getFullName)
                .filter(name -> !name.isBlank())
                .orElse(null);
        if (displayName == null) {
            displayName = firstNonBlank(fetchBusinessName(username), fetchUserFullName(username), username);
        }


        CommentAuthorAvatarEntity row = cached.orElseGet(CommentAuthorAvatarEntity::new);
        row.setUsername(username);
        row.setFullName(displayName);
        if (row.getFullName() != null && row.getFullName().length() > 255) {
            row.setFullName(row.getFullName().substring(0, 255));
        }
        if (isStale(row) || row.getDataUrl() == null) {
            String dataUrl = row.getDataUrl();
            try {
                dataUrl = fetchAvatar(username).orElse(dataUrl);
                row.setDataUrl(dataUrl);
                row.setFetchedAt(new Timestamp(System.currentTimeMillis()));
            } catch (Exception e) {
                log.debug("Avatar refresh skipped for {}: {}", username, e.getMessage());
            }
        }
        try {
            if (row.getFetchedAt() == null) {
                row.setFetchedAt(new Timestamp(System.currentTimeMillis()));
            }
            coreAuthorService.saveAuthor(row);
        } catch (Exception e) {
            // Cache write failure never blocks the comment itself.
            log.warn("Unable to cache author profile for {}: {}", username, e.getMessage());
        }
        return new AuthorSnapshot(displayName, row.getDataUrl());
    }

    /**
     * Cached avatar for the public endpoint; null means "no picture" and the
     * frontend falls back to initials. Never calls out (that path is
     * high-frequency and public).
     */
    public String cachedAvatar(String username) {
        return coreAuthorService.findAuthor(username)
                .map(CommentAuthorAvatarEntity::getDataUrl)
                .orElse(null);
    }

    private boolean isStale(CommentAuthorAvatarEntity row) {
        if (row.getFetchedAt() == null) {
            return true;
        }
        long ageMillis = System.currentTimeMillis() - row.getFetchedAt().getTime();
        return ageMillis > Duration.ofMinutes(cacheMinutes).toMillis();
    }

    // ------------------------------------------------------------------ lookups

    private Optional<String> fetchBusinessName(String username) {
        try {
            Envelope<SelfProfile> body = publiclinkClient.get()
                    .uri("/api/v1/account/self")
                    .header(USER_ID_HEADER, username)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Envelope<SelfProfile>>() {
                    })
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(error -> {
                        log.debug("Business-name lookup failed for {}: {}", username, error.getMessage());
                        return Mono.empty();
                    })
                    .block(Duration.ofSeconds(4));
            return Optional.ofNullable(body == null || body.data == null ? null : blankToNull(body.data.name));
        } catch (Exception e) {
            log.debug("Business-name lookup unavailable for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchUserFullName(String username) {
        try {
            Envelope<FoundUser> body = userClient.post()
                    .uri("/api/v1/user/findByUsername")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("userName", username))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Envelope<FoundUser>>() {
                    })
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(error -> {
                        log.debug("User-service name lookup failed for {}: {}", username, error.getMessage());
                        return Mono.empty();
                    })
                    .block(Duration.ofSeconds(4));
            return Optional.ofNullable(body == null || body.data == null ? null : blankToNull(body.data.name));
        } catch (Exception e) {
            log.debug("User-service name lookup unavailable for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchAvatar(String username) {
        Envelope<AvatarPayload> body = userClient.get()
                .uri("/api/v1/user/avatar")
                .header(USER_ID_HEADER, username)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Envelope<AvatarPayload>>() {
                })
                .timeout(Duration.ofSeconds(3))
                .block(Duration.ofSeconds(4));
        return Optional.ofNullable(body == null || body.data == null ? null : blankToNull(body.data.dataUrl));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(Optional<String> first, Optional<String> second, String fallback) {
        return first.map(String::trim)
                .or(() -> second.map(String::trim))
                .filter(value -> !value.isEmpty())
                .orElse(fallback);
    }

    /** Shape of the {success, data, ...} envelope both upstream services use. */
    private static class Envelope<T> {
        public boolean success;
        public T data;
    }

    /** Subset of publiclink-app's SelfProfileResponse (the business row's name). */
    private static class SelfProfile {
        public String name;
    }

    /** Subset of the user service's FindUserResponse. */
    private static class FoundUser {
        public String userName;
        public String name;
    }

    /** Subset of the user service's AvatarResponse. */
    private static class AvatarPayload {
        public String dataUrl;
    }
}
