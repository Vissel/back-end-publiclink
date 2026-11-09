package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatDirectionEnum;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.core.model.ChatStatusEnum;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import com.qrpublic.apartment.chat.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat business rules: what happens to a message on its way in from a client,
 * on
 * its way out to Zalo, and on the way back from the administrator.
 *
 * The domain owns the transaction boundary together with the core layer and
 * never
 * returns JPA entities; every state change is broadcast through
 * {@link ChatEventPublisher} so both windows stay live.
 */
@Slf4j
@Component
public class ChatMessageDomain {

    /** Source marker for replies that arrived from the Zalo app. */
    public static final String SOURCE_ZALO = "ZALO";

    /** Source marker for replies written in the administrator console. */
    public static final String SOURCE_CONSOLE = "CONSOLE";

    /**
     * Matches the {@code [Chat 0042]} header, or a bare tag the admin typed
     * themselves.
     */
    private static final Pattern TAG_PATTERN = Pattern.compile("\\[?\\s*chat\\s*#?(\\d{1,4})\\s*]?",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    CoreChatService coreChatService;

    @Autowired
    ZaloBridgeDomain zaloBridgeDomain;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    @Value("${chat.max-message-length:2000}")
    private int maxMessageLength;

    /**
     * How many recent client messages to scan when a reply carries no routing hint.
     */
    @Value("${chat.reply-scan-size:20}")
    private int replyScanSize;

    /** Ceiling for a broad seen update when a Zalo event omits message ids. */
    @Value("${chat.receipt-scan-size:100}")
    private int receiptScanSize;

    // ------------------------------------------------------------------ client

    /**
     * Stores a message typed by the client in the app chat and hands it to the
     * Zalo leg. A retried POST with the same client message id is answered with
     * the stored message instead of duplicating it.
     */
    public ChatMessageModel saveClientMessage(Long conversationId, String clientMessageId, String content) {
        if (content == null || content.isBlank()) {
            throw new ApplicationException("Message content is required.", 400);
        }

        Optional<ChatMessageModel> replayed = coreChatService.findByClientMessageId(clientMessageId);
        if (replayed.isPresent()) {
            log.info("Duplicate client message POST ignored: clientMessageId={}", clientMessageId);
            return replayed.get();
        }

        ChatConversationModel conversation = coreChatService.findConversationById(conversationId)
                .orElseThrow(() -> new ApplicationException("Conversation not found: " + conversationId, 404));

        ChatMessageModel saved = coreChatService.saveMessage(conversation.getId(), ChatDirectionEnum.CLIENT,
                clip(content), blankToNull(clientMessageId), null);
        chatEventPublisher.publishMessage(saved);

        dispatchToZalo(saved, conversation);
        return saved;
    }

    private void dispatchToZalo(ChatMessageModel message, ChatConversationModel conversation) {
        if (!zaloBridgeDomain.isBridgeEnabled()) {
            // Console mode: the administrator inbox is the delivery channel, so the
            // message is considered accepted rather than stuck in PENDING.
            coreChatService.advanceStatus(message.getId(), ChatStatusEnum.SENT, null, null)
                    .ifPresent(chatEventPublisher::publishStatus);
            log.debug("Zalo bridge disabled, message {} queued for console only", message.getId());
            return;
        }
        // Fire and forget: the client already has the message on screen and learns
        // the Zalo outcome through the status event stream.
        zaloBridgeDomain.dispatchClientMessage(message)
                .subscribe(
                        outcome -> log.info("Zalo dispatch message {}: delivered={}, zaloMessageId={}, reason={}",
                                message.getId(), outcome.isDelivered(), outcome.getZaloMessageId(),
                                outcome.getReason()),
                        error -> log.error("Zalo dispatch crashed for message {}: {}",
                                message.getId(), error.getMessage()));
    }

    // --------------------------------------------------------------- dispatcher

    /**
     * Routes the administrator's answer to the conversation it belongs to.
     *
     * Zalo gives one thread for every customer, so a reply must be attributed:
     * 1. the quoted Zalo message id (the admin used Reply on one customer line);
     * 2. a {@code [Chat 0042]} tag inside the text;
     * 3. failing both, the newest conversation still waiting for an answer.
     */
    public ChatMessageModel routeAdminReply(String content, String quoteZaloMsgId, String adminZaloMessageId) {
        // A replayed webhook (or a scheduler re-drive) must never add the answer twice.
        Optional<ChatMessageModel> alreadyStored = coreChatService.findByZaloMessageId(adminZaloMessageId);
        if (alreadyStored.isPresent()) {
            log.info("Zalo answer already stored as message {}, duplicate event skipped",
                    alreadyStored.get().getId());
            return alreadyStored.get();
        }

        Optional<ChatConversationModel> target = resolveConversation(quoteZaloMsgId, content);
        if (target.isEmpty()) {
            log.warn("Zalo answer not attributable to any conversation, ignored. quote={}, text={}",
                    quoteZaloMsgId, clip(content));
            return null;
        }
        return saveAdminMessage(target.get(), content, quoteZaloMsgId, adminZaloMessageId, SOURCE_ZALO);
    }

    /**
     * Administrator answers from the console; mirrors into Zalo only when the
     * bridge is on.
     */
    public ChatMessageModel consoleReply(Long conversationId, String content, String clientTag) {
        ChatConversationModel conversation = coreChatService.findConversationById(conversationId)
                .orElseThrow(() -> new ApplicationException("Conversation not found: " + conversationId, 404));
        ChatMessageModel reply = saveAdminMessage(conversation, content, null, null, SOURCE_CONSOLE);

        if (zaloBridgeDomain.isBridgeEnabled()) {
            // Keeps the Zalo thread honest: the admin sees in Zalo what they answered
            // elsewhere.
            zaloBridgeDomain.dispatchAdminReply(clientTag, content, null)
                    .subscribe(outcome -> log.info("Console reply mirrored to Zalo: delivered={}, reason={}",
                            outcome.isDelivered(), outcome.getReason()),
                            error -> log.warn("Console reply mirror failed: {}", error.getMessage()));
        }
        return reply;
    }

    private ChatMessageModel saveAdminMessage(ChatConversationModel conversation, String content,
            String quoteZaloMsgId, String adminZaloMessageId, String source) {
        Long quotedMessageId = resolveQuotedMessageId(quoteZaloMsgId);
        ChatStatusEnum initial = chatEventPublisher.isClientOnline(conversation.getDeviceId())
                ? ChatStatusEnum.SENT
                : ChatStatusEnum.PENDING;

        ChatMessageModel reply = coreChatService.saveMessage(conversation.getId(), ChatDirectionEnum.ADMIN,
                clip(content), null, quotedMessageId);
        reply = coreChatService.advanceStatus(reply.getId(), initial, adminZaloMessageId, null).orElse(reply);
        chatEventPublisher.publishMessage(reply);

        markAnswered(conversation.getId(), reply, quotedMessageId);
        log.info("Administrator reply stored for conversation {} (source={}, status={})",
                conversation.getUuid(), source, reply.getStatus());
        return reply;
    }

    /**
     * Flags the client message this answer reacts to. An explicitly quoted message
     * wins; otherwise the newest still-unanswered client message of the
     * conversation
     * is used, which is what a linear support thread looks like.
     */
    private void markAnswered(Long conversationId, ChatMessageModel reply, Long quotedMessageId) {
        Long targetId = quotedMessageId != null ? quotedMessageId
                : lastUnansweredClientId(conversationId, reply.getId());
        if (targetId == null) {
            return;
        }
        coreChatService.advanceStatus(targetId, ChatStatusEnum.REPLIED, null, null)
                .ifPresent(chatEventPublisher::publishStatus);
    }

    private Long lastUnansweredClientId(Long conversationId, Long excludeMessageId) {
        List<ChatMessageModel> messages = coreChatService.listMessages(conversationId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageModel candidate = messages.get(i);
            if (candidate.getDirection() == ChatDirectionEnum.CLIENT
                    && !candidate.getId().equals(excludeMessageId)
                    && candidate.getStatus() != ChatStatusEnum.REPLIED) {
                return candidate.getId();
            }
        }
        return null;
    }

    private Long resolveQuotedMessageId(String quoteZaloMsgId) {
        if (quoteZaloMsgId == null || quoteZaloMsgId.isBlank()) {
            return null;
        }
        return coreChatService.findByZaloMessageId(quoteZaloMsgId)
                .map(ChatMessageModel::getId)
                .orElse(null);
    }

    private Optional<ChatConversationModel> resolveConversation(String quoteZaloMsgId, String content) {
        Long quotedMessageId = resolveQuotedMessageId(quoteZaloMsgId);
        if (quotedMessageId != null) {
            Optional<ChatConversationModel> quoted = coreChatService.findByZaloMessageId(quoteZaloMsgId)
                    .flatMap(message -> coreChatService.findConversationById(message.getConversationId()));
            if (quoted.isPresent()) {
                return quoted;
            }
        }

        String tag = extractTag(content);
        Optional<ChatConversationModel> tagged = coreChatService.findConversationByClientTag(tag);
        if (tagged.isPresent()) {
            return tagged;
        }

        return findAwaitingConversation();
    }

    private Optional<ChatConversationModel> findAwaitingConversation() {
        List<ChatMessageModel> recent = coreChatService.findRecentMessages(ChatDirectionEnum.CLIENT, replyScanSize);
        for (ChatMessageModel candidate : recent) {
            Optional<ChatMessageModel> last = coreChatService.findLastMessage(candidate.getConversationId());
            if (last.isPresent() && last.get().getDirection() == ChatDirectionEnum.CLIENT) {
                return coreChatService.findConversationById(candidate.getConversationId());
            }
        }
        return Optional.empty();
    }

    static String extractTag(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = TAG_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ----------------------------------------------------------------- receipts

    /**
     * Applies a Zalo receipt (delivered / seen) addressed by the Zalo message id.
     */
    public Optional<ChatMessageModel> applyReceipt(String zaloMessageId, ChatStatusEnum status) {
        Optional<ChatMessageModel> updated = coreChatService.findByZaloMessageId(zaloMessageId)
                .flatMap(message -> coreChatService.advanceStatus(message.getId(), status, null, null));
        updated.ifPresent(chatEventPublisher::publishStatus);
        return updated;
    }

    /**
     * Broad seen update for events that omit message identification: every message
     * already handed to Zalo is treated as opened, because the admin had to have
     * the
     * thread in front of them to trigger the event.
     */
    public int applyReceiptBroadly(ChatStatusEnum status) {
        List<ChatMessageModel> pending = coreChatService.listDispatchedMessages(ChatDirectionEnum.CLIENT,
                receiptScanSize);
        int updated = 0;
        for (ChatMessageModel message : pending) {
            if (message.getStatus().isAtOrAfter(status)) {
                continue;
            }
            Optional<ChatMessageModel> advanced = coreChatService.advanceStatus(message.getId(), status, null, null);
            if (advanced.isPresent()) {
                chatEventPublisher.publishStatus(advanced.get());
                updated++;
            }
        }
        return updated;
    }

    /**
     * The client opened the chat window: its administrator messages become seen.
     */
    public void markAdminMessagesSeenByClient(Long conversationId) {
        for (ChatMessageModel message : coreChatService.listMessages(conversationId)) {
            if (message.getDirection() == ChatDirectionEnum.ADMIN
                    && !message.getStatus().isAtOrAfter(ChatStatusEnum.SEEN)) {
                coreChatService.advanceStatus(message.getId(), ChatStatusEnum.DELIVERED, null, null)
                        .ifPresent(chatEventPublisher::publishStatus);
                coreChatService.advanceStatus(message.getId(), ChatStatusEnum.SEEN, null, null)
                        .ifPresent(chatEventPublisher::publishStatus);
            }
        }
    }

    // ------------------------------------------------------------------ reads

    public List<ChatMessageModel> listMessages(Long conversationId) {
        return coreChatService.listMessages(conversationId);
    }

    /** Anything a reconnecting visitor missed while the stream was closed. */
    public List<ChatMessageModel> replay(Long conversationId, Long afterMessageId) {
        return coreChatService.replayAfter(conversationId, afterMessageId);
    }

    public void publishTyping(String deviceId, String conversationUuid, String source, boolean typing) {
        chatEventPublisher.publishTyping(deviceId, conversationUuid, source, typing);
    }

    private String clip(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new ApplicationException("Message content is required.", 400);
        }
        return trimmed.length() > maxMessageLength ? trimmed.substring(0, maxMessageLength) : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
