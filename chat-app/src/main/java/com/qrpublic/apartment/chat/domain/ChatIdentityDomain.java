package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatDeviceModel;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import com.qrpublic.apartment.chat.util.ChatIdentityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recognises a chat visitor across visits.
 *
 * Account first: a signed-in caller is identified by the username the gateway
 * forwards from the validated JWT, and their thread is resolved by that owner
 * alone. The device ladder below only serves anonymous visitors, and only
 * among conversations with no owner - two accounts on one browser share local
 * storage and therefore a device id, so device identity must never merge them.
 *
 * Anonymous matching ladder, most to least reliable:
 * 1. the device id the client keeps in local storage;
 * 2. a salted attribute fingerprint, exact enough above the configured
 * threshold;
 * 3. fingerprint plus identical hashed IP, for a browser that changed slightly;
 * 4. otherwise a brand-new device.
 *
 * A visitor who "loses the system" — closed tab, cleared storage, switched
 * network — therefore lands back on the same conversation whenever any of the
 * first three signals survive. Matching never trusts the IP alone: mobile
 * subscribers in Vietnam share carrier-grade NAT addresses.
 */
@Slf4j
@Component
public class ChatIdentityDomain {

    private static final String RECOGNITION_NEW = "NEW";
    private static final String RECOGNITION_DEVICE_ID = "DEVICE_ID";
    private static final String RECOGNITION_FINGERPRINT = "FINGERPRINT";
    private static final String RECOGNITION_IP = "IP";
    private static final String RECOGNITION_ACCOUNT = "ACCOUNT";

    @Autowired
    CoreChatService coreChatService;

    @Value("${chat.identity.salt:publiclink-chat-salt}")
    private String identitySalt;

    /** Attribute agreement ratio required to adopt a device without its id. */
    @Value("${chat.identity.fingerprint-threshold:0.8}")
    private double fingerprintThreshold;

    /** Lower bound accepted only when the hashed IP also matches. */
    @Value("${chat.identity.partial-threshold:0.5}")
    private double partialThreshold;

    @Value("${chat.identity.candidate-limit:200}")
    private int candidateLimit;

    public ChatIdentityResult identify(String submittedDeviceId, List<String> attributes,
            String clientIp, String userAgent, String ownerUsername, String ownerDisplayName) {
        String fingerprint = ChatIdentityUtils.buildFingerprintToken(attributes, identitySalt);
        String ipHash = ChatIdentityUtils.hashIp(clientIp, identitySalt);

        boolean signedIn = ownerUsername != null && !ownerUsername.isBlank();
        ChatDeviceModel device = recogniseDevice(submittedDeviceId, fingerprint, ipHash, !signedIn);
        ChatDeviceModel registered = coreChatService.registerVisit(device.getDeviceId(), fingerprint, ipHash,
                userAgent);

        ChatIdentityResult result = new ChatIdentityResult();
        result.setDevice(registered);

        if (signedIn) {
            // The account owns the thread. The device is still recorded for
            // analytics, but it never decides which conversation continues.
            result.setRecognition(RECOGNITION_ACCOUNT);
            Optional<ChatConversationModel> owned = coreChatService
                    .findLatestConversationByOwner(ownerUsername.trim());
            if (owned.isPresent() && !isClosed(owned.get())) {
                result.setConversation(owned.get());
                result.setResumed(true);
            } else {
                result.setConversation(coreChatService.createConversation(registered.getDeviceId(),
                        ownerUsername.trim(), ownerDisplayName));
                result.setResumed(false);
            }
        } else {
            result.setRecognition(device.getRecognition());
            Optional<ChatConversationModel> existing = coreChatService
                    .findLatestAnonymousConversationByDeviceId(registered.getDeviceId());
            if (existing.isPresent() && !isClosed(existing.get())) {
                result.setConversation(existing.get());
                result.setResumed(true);
            } else {
                result.setConversation(coreChatService.createConversation(registered.getDeviceId(), null, null));
                result.setResumed(false);
            }
        }
        log.info("Chat visitor identified: recognition={}, owner={}, deviceId={}, resumed={}, visits={}",
                result.getRecognition(), ownerUsername, registered.getDeviceId(), result.isResumed(),
                registered.getVisitCount());
        return result;
    }

    /**
     * Device recognition for analytics and for anonymous visitors only. When
     * {@code anonymous} is true, devices that carry a signed-in account's
     * conversation are never adopted: a signed-out visitor must not latch onto
     * an account's device row and overwrite its fingerprint, IP hash or visit
     * counter (and must never reach the account's thread).
     */
    private ChatDeviceModel recogniseDevice(String submittedDeviceId, String fingerprint, String ipHash,
            boolean anonymous) {
        // 1. the client still holds its own device id
        if (submittedDeviceId != null && !submittedDeviceId.isBlank()) {
            String trimmed = submittedDeviceId.trim();
            Optional<ChatDeviceModel> known = coreChatService.findDeviceByDeviceId(trimmed);
            if (known.isPresent() && !(anonymous && coreChatService.deviceHasOwnedConversation(trimmed))) {
                ChatDeviceModel device = known.get();
                device.setRecognition(RECOGNITION_DEVICE_ID);
                return device;
            }
        }

        // 2/3. local storage is gone: re-identify from browser characteristics
        if (fingerprint != null && fingerprint.contains(":")) {
            ChatDeviceModel best = null;
            double bestScore = 0d;
            boolean bestIpMatch = false;

            for (ChatDeviceModel candidate : coreChatService.listDeviceCandidates(candidateLimit)) {
                if (anonymous && coreChatService.deviceHasOwnedConversation(candidate.getDeviceId())) {
                    continue;
                }
                double score = ChatIdentityUtils.similarity(fingerprint, candidate.getFingerprint());
                boolean sameIp = ipHash != null && ipHash.equals(candidate.getIpHash());
                boolean eligible = score >= fingerprintThreshold || (sameIp && score >= partialThreshold);
                if (eligible && (score > bestScore || (score == bestScore && sameIp && !bestIpMatch))) {
                    best = candidate;
                    bestScore = score;
                    bestIpMatch = sameIp;
                }
            }

            if (best != null) {
                best.setRecognition(bestIpMatch && bestScore < fingerprintThreshold
                        ? RECOGNITION_IP
                        : RECOGNITION_FINGERPRINT);
                return best;
            }
        }

        // 4. genuinely new visitor
        ChatDeviceModel fresh = new ChatDeviceModel();
        fresh.setDeviceId(newDeviceId());
        fresh.setRecognition(RECOGNITION_NEW);
        return fresh;
    }

    private static boolean isClosed(ChatConversationModel conversation) {
        return CoreChatService.ChatConversationStatus.CLOSED.equals(conversation.getStatus());
    }

    private static String newDeviceId() {
        return UUID.randomUUID().toString();
    }
}
