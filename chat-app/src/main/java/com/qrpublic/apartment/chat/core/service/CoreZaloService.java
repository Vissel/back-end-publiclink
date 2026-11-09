package com.qrpublic.apartment.chat.core.service;

import com.qrpublic.apartment.chat.core.model.ZaloCredentialModel;
import com.qrpublic.apartment.chat.entity.ZaloCredential;
import com.qrpublic.apartment.chat.entity.ZaloEventLog;
import com.qrpublic.apartment.chat.repository.ZaloCredentialRepository;
import com.qrpublic.apartment.chat.repository.ZaloEventLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Core layer for Zalo side data: OAuth credentials, the bound administrator
 * account and the raw webhook inbox.
 */
@Slf4j
@Service
public class CoreZaloService {

    /** OA OAuth material: value_a = access token, value_b = refresh token. */
    public static final String SCOPE_OA_TOKEN = "OA_TOKEN";

    /** Zalo user id of the administrator who answers chats from the Zalo app. */
    public static final String SCOPE_ADMIN_USER = "ADMIN_USER";

    @Autowired
    ZaloCredentialRepository zaloCredentialRepository;

    @Autowired
    ZaloEventLogRepository zaloEventLogRepository;

    // ------------------------------------------------------------ credentials

    @Transactional(readOnly = true)
    public Optional<ZaloCredentialModel> findCredential(String scopeKey) {
        return zaloCredentialRepository.findByScopeKey(scopeKey).map(this::toModel);
    }

    @Transactional
    public ZaloCredentialModel saveToken(String accessToken, String refreshToken, long expiresAtEpochMillis) {
        ZaloCredential credential = zaloCredentialRepository.findByScopeKey(SCOPE_OA_TOKEN)
                .orElseGet(ZaloCredential::new);
        credential.setScopeKey(SCOPE_OA_TOKEN);
        credential.setValueA(accessToken);
        // A refresh may come without a new refresh token: keep the previous one.
        if (refreshToken != null && !refreshToken.isBlank()) {
            credential.setValueB(refreshToken);
        }
        credential.setExpiresAt(expiresAtEpochMillis);
        credential.setUpdatedAt(Timestamp.from(Instant.now()));
        return toModel(zaloCredentialRepository.save(credential));
    }

    @Transactional
    public ZaloCredentialModel bindAdminUser(String zaloUserId) {
        ZaloCredential credential = zaloCredentialRepository.findByScopeKey(SCOPE_ADMIN_USER)
                .orElseGet(ZaloCredential::new);
        credential.setScopeKey(SCOPE_ADMIN_USER);
        credential.setValueA(zaloUserId);
        credential.setUpdatedAt(Timestamp.from(Instant.now()));
        log.info("Zalo chat administrator bound to user id {}", zaloUserId);
        return toModel(zaloCredentialRepository.save(credential));
    }

    // -------------------------------------------------------------- event log

    /**
     * Persist an inbound webhook event. Returns the stored row id, or null when the
     * event id is already known — Zalo retries the same event up to five times.
     */
    @Transactional
    public Long recordEvent(String eventId, String eventName, String payload) {
        if (eventId == null || eventId.isBlank()) {
            // No usable id: cannot de-duplicate, still keep the row for auditing.
            eventId = "anon-" + System.nanoTime();
        }
        if (zaloEventLogRepository.existsByEventId(eventId)) {
            return null;
        }
        ZaloEventLog entry = new ZaloEventLog();
        entry.setEventId(eventId);
        entry.setEventName(eventName);
        entry.setPayload(payload);
        entry.setProcessed(false);
        entry.setCreatedAt(Timestamp.from(Instant.now()));
        return zaloEventLogRepository.save(entry).getId();
    }

    @Transactional
    public void markEventProcessed(Long id) {
        if (id != null) {
            zaloEventLogRepository.markProcessed(id);
        }
    }

    @Transactional(readOnly = true)
    public List<ZaloEventLog> findUnprocessedEvents(int limit) {
        return zaloEventLogRepository.findUnprocessed(PageRequest.of(0, limit));
    }

    private ZaloCredentialModel toModel(ZaloCredential credential) {
        ZaloCredentialModel model = new ZaloCredentialModel();
        model.setId(credential.getId());
        model.setScopeKey(credential.getScopeKey());
        model.setValueA(credential.getValueA());
        model.setValueB(credential.getValueB());
        model.setExpiresAt(credential.getExpiresAt());
        return model;
    }
}
