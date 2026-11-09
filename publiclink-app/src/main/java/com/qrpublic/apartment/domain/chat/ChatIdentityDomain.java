package com.qrpublic.apartment.domain.chat;

import com.qrpublic.apartment.core.model.ChatConversationModel;
import com.qrpublic.apartment.core.model.ChatDeviceModel;
import com.qrpublic.apartment.core.service.CoreChatService;
import com.qrpublic.apartment.util.ChatIdentityUtils;
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
 * Matching ladder, most to least reliable:
 * 1. the device id the client keeps in local storage;
 * 2. a salted attribute fingerprint, exact enough above the configured threshold;
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
                                       String clientIp, String userAgent) {
        String fingerprint = ChatIdentityUtils.buildFingerprintToken(attributes, identitySalt);
        String ipHash = ChatIdentityUtils.hashIp(clientIp, identitySalt);

        ChatDeviceModel device = recogniseDevice(submittedDeviceId, fingerprint, ipHash);
        ChatDeviceModel registered = coreChatService.registerVisit(device.getDeviceId(), fingerprint, ipHash,
                userAgent);

        ChatIdentityResult result = new ChatIdentityResult();
        result.setDevice(registered);
        result.setRecognition(device.getRecognition());

        Optional<ChatConversationModel> existing =
                coreChatService.findLatestConversationByDeviceId(registered.getDeviceId());
        if (existing.isPresent() && !isClosed(existing.get())) {
            result.setConversation(existing.get());
            result.setResumed(true);
        } else {
            result.setConversation(coreChatService.createConversation(registered.getDeviceId()));
            result.setResumed(false);
        }
        log.info("Chat visitor identified: recognition={}, deviceId={}, resumed={}, visits={}",
                result.getRecognition(), registered.getDeviceId(), result.isResumed(), registered.getVisitCount());
        return result;
    }

    private ChatDeviceModel recogniseDevice(String submittedDeviceId, String fingerprint, String ipHash) {
        // 1. the client still holds its own device id
        if (submittedDeviceId != null && !submittedDeviceId.isBlank()) {
            Optional<ChatDeviceModel> known = coreChatService.findDeviceByDeviceId(submittedDeviceId.trim());
            if (known.isPresent()) {
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
                        ? RECOGNITION_IP : RECOGNITION_FINGERPRINT);
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
