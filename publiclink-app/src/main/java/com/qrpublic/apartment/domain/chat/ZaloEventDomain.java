package com.qrpublic.apartment.domain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.core.model.ChatStatusEnum;
import com.qrpublic.apartment.core.model.ZaloCredentialModel;
import com.qrpublic.apartment.core.service.CoreZaloService;
import com.qrpublic.apartment.entity.ZaloEventLog;
import com.qrpublic.apartment.util.ZaloSignatureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Inbound Zalo webhook processing: authenticate, de-duplicate, then translate a
 * Zalo event into chat state.
 *
 * Handled events:
 * - {@code user_send_text} and friends: the administrator answered in the Zalo app;
 * - {@code user_received_message}: the message reached the administrator's device;
 * - {@code user_seen_message}: the administrator opened the Zalo chat;
 * - {@code follow} / {@code unfollow}: kept for auditing.
 *
 * Processing runs off the request thread: Zalo drops a webhook that is not
 * answered with HTTP 200 within two seconds, and disables it after five failed
 * retries. Unfinished rows are re-driven by the scheduler below.
 */
@Slf4j
@Component
public class ZaloEventDomain {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    CoreZaloService coreZaloService;

    @Autowired
    ChatMessageDomain chatMessageDomain;

    @Value("${zalo.app-id:}")
    private String appId;

    @Value("${zalo.oa-secret-key:}")
    private String oaSecretKey;

    /** Turn off only for local experimentation; never in production. */
    @Value("${zalo.verify-signature:true}")
    private boolean verifySignature;

    /** Text the administrator sends once to bind their personal Zalo account. */
    @Value("${zalo.admin-link-code:LINK-ADMIN}")
    private String adminLinkCode;

    /** Fallback administrator identity when no binding has happened yet. */
    @Value("${zalo.admin-user-id:}")
    private String configuredAdminUserId;

    @Value("${zalo.receipt-scan-on-broad-events:true}")
    private boolean receiptScanOnBroadEvents;

    /**
     * Authenticates a delivery. The signature covers appId + raw data + timestamp
     * + OA secret key, exactly as Zalo specifies.
     */
    public boolean isAuthentic(String rawBody, String signatureHeader) {
        if (!verifySignature) {
            log.warn("Zalo webhook signature verification is DISABLED (zalo.verify-signature=false)");
            return true;
        }
        if (oaSecretKey == null || oaSecretKey.isBlank()) {
            log.error("zalo.oa-secret-key is not configured; refusing unsigned webhook");
            return false;
        }
        EventEnvelope envelope = parseEnvelope(rawBody);
        String provided = firstNonBlank(signatureHeader, envelope.mac());
        if (provided == null) {
            log.warn("Rejected Zalo webhook without a signature");
            return false;
        }
        String expected = ZaloSignatureUtils.sign(appId, envelope.data(), envelope.timestamp(), oaSecretKey);
        boolean matches = ZaloSignatureUtils.matches(expected, provided);
        if (!matches) {
            log.warn("Rejected Zalo webhook with a non-matching signature");
        }
        return matches;
    }

    /**
     * Stores and processes one event. Returns false when the event was already
     * recorded (a Zalo retry) so the caller can log the duplicate.
     */
    public boolean accept(String rawBody) {
        EventEnvelope envelope = parseEnvelope(rawBody);
        JsonNode event = envelope.eventNode();
        String eventName = text(event, "event_name", "eventName", "type");
        String eventId = resolveEventId(event, rawBody);

        Long storedId = coreZaloService.recordEvent(eventId, eventName, envelope.data());
        if (storedId == null) {
            log.info("Duplicate Zalo event ignored: event={}, id={}", eventName, eventId);
            return false;
        }
        dispatch(eventName, event);
        coreZaloService.markEventProcessed(storedId);
        return true;
    }

    private void dispatch(String eventName, JsonNode event) {
        try {
            if (eventName == null) {
                log.warn("Zalo event without event_name received, ignored");
                return;
            }
            switch (eventName) {
                case "user_send_text" -> handleAdminAnswer(event, extractText(event));
                case "user_send_image", "user_send_file", "user_send_video", "user_send_audio",
                        "user_send_sticker", "user_send_gif", "user_send_location",
                        "user_send_business_card", "user_send_link" ->
                        handleAdminAnswer(event, describeAttachment(eventName));
                case "user_received_message" -> handleReceipt(event, ChatStatusEnum.DELIVERED);
                case "user_seen_message" -> handleReceipt(event, ChatStatusEnum.SEEN);
                case "follow", "unfollow" -> log.info("Zalo follower event {}: user={}",
                        eventName, text(event, "user_id_by_app"));
                default -> log.info("Unsupported Zalo event ignored: {}", eventName);
            }
        } catch (Exception e) {
            // Never let one event kill the queue: Zalo will retry it anyway.
            log.error("Failed to process Zalo event {}: {}", eventName, e.getMessage(), e);
        }
    }

    private void handleAdminAnswer(JsonNode event, String content) {
        String senderId = text(event, "sender.id", "user_id", "sender_id");

        // First-time binding: the administrator proves ownership of their personal Zalo
        // account by sending the agreed code. A blank code must never match anything.
        if (!isAdministrator(senderId) && isBindingRequest(content)) {
            coreZaloService.bindAdminUser(senderId);
            return;
        }

        // Anyone else who happens to DM the OA is a customer, not the support desk.
        // Routing their message as an administrator answer would inject fake replies
        // into somebody's conversation.
        if (!isAdministrator(senderId)) {
            log.info("Ignoring Zalo message from non-administrator user {}", senderId);
            return;
        }

        if (content == null || content.isBlank()) {
            log.info("Zalo answer without usable text ignored: event sender={}", senderId);
            return;
        }
        String quote = text(event, "quote_msg_id", "quoteMsgId");
        String zaloMessageId = text(event, "message.msg_id", "message_id", "msg_id");
        chatMessageDomain.routeAdminReply(stripTagPrefix(content), quote, zaloMessageId);
    }

    private boolean isBindingRequest(String content) {
        if (content == null || content.isBlank() || adminLinkCode == null || adminLinkCode.isBlank()) {
            return false;
        }
        return content.toUpperCase().contains(adminLinkCode.toUpperCase());
    }

    /**
     * The configured administrator, or the one bound through the LINK-ADMIN handshake.
     * Until either exists nothing can be an answer, so nothing is routed.
     */
    private boolean isAdministrator(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return false;
        }
        if (configuredAdminUserId != null && !configuredAdminUserId.isBlank()) {
            return configuredAdminUserId.trim().equals(senderId);
        }
        return coreZaloService.findCredential(CoreZaloService.SCOPE_ADMIN_USER)
                .map(ZaloCredentialModel::getValueA)
                .filter(senderId::equals)
                .isPresent();
    }

    private void handleReceipt(JsonNode event, ChatStatusEnum status) {
        Optional<String> zaloMessageId = Optional.ofNullable(text(event,
                "message_id", "msg_id", "message.msg_id", "data.message_id"));
        if (zaloMessageId.isPresent()) {
            chatMessageDomain.applyReceipt(zaloMessageId.get(), status)
                    .ifPresent(message -> log.info("Zalo receipt {} applied to message {}",
                            status, message.getId()));
            return;
        }
        if (receiptScanOnBroadEvents) {
            int updated = chatMessageDomain.applyReceiptBroadly(status);
            log.info("Zalo {} event without message id: {} messages advanced", status, updated);
        }
    }

    /** Re-drives events that failed or were stored but not processed. */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void retryUnprocessedEvents() {
        List<ZaloEventLog> pending = coreZaloService.findUnprocessedEvents(50);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-driving {} unprocessed Zalo events", pending.size());
        for (ZaloEventLog entry : pending) {
            try {
                dispatch(entry.getEventName(), readTree(entry.getPayload()));
                coreZaloService.markEventProcessed(entry.getId());
            } catch (Exception e) {
                log.error("Retry of Zalo event {} failed: {}", entry.getId(), e.getMessage());
            }
        }
    }

    /** Strips our own {@code [Chat 0042]} header if the admin quoted it back verbatim. */
    private static String stripTagPrefix(String content) {
        String trimmed = content.trim();
        if (ChatMessageDomain.extractTag(trimmed) != null && trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close > 0 && close < trimmed.length() - 1) {
                return trimmed.substring(close + 1).trim();
            }
        }
        return trimmed;
    }

    private static String describeAttachment(String eventName) {
        return switch (eventName) {
            case "user_send_image" -> "[image]";
            case "user_send_file" -> "[file]";
            case "user_send_video" -> "[video]";
            case "user_send_audio" -> "[voice]";
            case "user_send_sticker", "user_send_gif" -> "[sticker]";
            case "user_send_location" -> "[location]";
            case "user_send_business_card" -> "[contact]";
            case "user_send_link" -> "[link]";
            default -> "[attachment]";
        };
    }

    private EventEnvelope parseEnvelope(String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        JsonNode root = readTree(body);
        JsonNode dataField = root.get("data");
        if (dataField != null && dataField.isTextual()) {
            // {"data": "<json string>", "mac": "...", "timestamp": "..."} — the OA style
            JsonNode inner = readTree(dataField.asText());
            return new EventEnvelope(dataField.asText(),
                    text(root, "mac", "x-zevent-signature"),
                    text(root, "timestamp", "ts"),
                    inner);
        }
        return new EventEnvelope(body, text(root, "mac"), text(root, "timestamp"), root);
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            log.warn("Unparseable Zalo payload treated as empty: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    /** Reads the first dotted path that resolves to a non-empty text value. */
    private static String text(JsonNode node, String... paths) {
        if (node == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode cursor = node;
            for (String segment : path.split("\\.")) {
                if (cursor == null) {
                    break;
                }
                cursor = cursor.get(segment);
            }
            if (cursor != null && cursor.isValueNode()) {
                String value = cursor.asText();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static String extractText(JsonNode event) {
        return text(event, "message.text", "text", "message.content", "data.text");
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    private static String resolveEventId(JsonNode event, String rawBody) {
        String msgId = text(event, "message.msg_id", "msg_id", "message_id");
        if (msgId != null) {
            return msgId;
        }
        String timestamp = text(event, "timestamp");
        // Fall back to a digest of the payload so a retry carries the same identity.
        return "sha-" + ZaloSignatureUtils.sha256Hex(nullToEmpty(timestamp) + "|" + nullToEmpty(rawBody))
                .substring(0, 32);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Normalised view of a webhook delivery: the exact bytes the signature covers
     * plus the event object, whichever Zalo envelope style was used.
     */
    private record EventEnvelope(String data, String mac, String timestamp, JsonNode eventNode) {
        EventEnvelope {
            data = data == null ? "" : data;
        }
    }
}
