package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Conversation lifecycle: opening, resuming, closing, and detecting that a
 * visitor
 * lost the system or came back.
 *
 * A conversation is never deleted when the client disappears — it is marked
 * IDLE and
 * reopened on the next recognised visit, which is what makes "n-th return" land
 * on
 * the same thread with its history intact.
 */
@Slf4j
@Component
public class ChatConversationDomain {

    @Autowired
    CoreChatService coreChatService;

    @Autowired
    ChatEventStreamRegistry chatEventStreamRegistry;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    /** Quiet period after which a disconnected conversation is parked as IDLE. */
    @Value("${chat.idle-after-minutes:15}")
    private long idleAfterMinutes;

    /**
     * Ceiling of the idle sweep, so a very large inbox cannot stall the scheduler.
     */
    @Value("${chat.idle-scan-size:500}")
    private int idleScanSize;

    public Optional<ChatConversationModel> findByUuid(String uuid) {
        return coreChatService.findConversationByUuid(uuid);
    }

    /**
     * Newest anonymous conversation of a device, if the visitor never got a
     * start response. Account threads are resolved by owner, never by device.
     */
    public Optional<ChatConversationModel> findLatestByDeviceId(String deviceId) {
        return coreChatService.findLatestAnonymousConversationByDeviceId(deviceId);
    }

    /** Newest thread of a signed-in account. */
    public Optional<ChatConversationModel> findLatestByOwner(String ownerUsername) {
        return coreChatService.findLatestConversationByOwner(ownerUsername);
    }

    public List<ChatConversationModel> listForAdmin(int page, int size) {
        return coreChatService.listConversations(page, size);
    }

    public long countConversations() {
        return coreChatService.countConversations();
    }

    public Optional<ChatConversationModel> close(Long conversationId) {
        return coreChatService.updateConversationStatus(conversationId,
                CoreChatService.ChatConversationStatus.CLOSED);
    }

    /**
     * One more unread guest message. Returns the refreshed conversation so the
     * caller can broadcast the new badge to every open console.
     */
    public Optional<ChatConversationModel> markUnread(Long conversationId) {
        coreChatService.incrementUnread(conversationId);
        return coreChatService.findConversationById(conversationId);
    }

    /** The administrator opened the thread: clear the badge, same broadcast shape. */
    public Optional<ChatConversationModel> markRead(Long conversationId) {
        coreChatService.resetUnread(conversationId);
        return coreChatService.findConversationById(conversationId);
    }

    /** Legacy threads have no stream key; mint one before it is handed out. */
    public ChatConversationModel ensureStreamKey(Long conversationId) {
        return coreChatService.ensureStreamKey(conversationId);
    }

    /** Keeps the console's human name in step with the account profile. */
    public void refreshOwnerName(Long conversationId, String displayName) {
        coreChatService.updateOwnerDisplayName(conversationId, displayName);
    }

    /**
     * Called when a client stream opens: the administrator sees the visitor come
     * back.
     */
    public void onClientConnected(ChatConversationModel conversation) {
        coreChatService.updateConversationStatus(conversation.getId(),
                CoreChatService.ChatConversationStatus.ACTIVE);
        chatEventPublisher.publishPresence(conversation.getUuid(), true);
    }

    /**
     * Called when a client stream closes (tab shut, network dropped, proxy
     * timeout).
     */
    public void onClientDisconnected(String conversationUuid) {
        chatEventPublisher.publishPresence(conversationUuid, false);
        coreChatService.findConversationByUuid(conversationUuid)
                .filter(conversation -> !chatEventStreamRegistry.isClientOnline(conversationUuid))
                .ifPresent(conversation -> coreChatService.updateConversationStatus(conversation.getId(),
                        CoreChatService.ChatConversationStatus.IDLE));
    }

    /**
     * Safety net for connections that vanished without a close signal — a hard
     * client crash never reaches the stream's doFinally. Anything silent for longer
     * than the idle window and without a live stream is parked.
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void sweepIdleConversations() {
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(idleAfterMinutes * 60));
        List<ChatConversationModel> conversations = coreChatService.listConversations(0, idleScanSize);
        int parked = 0;
        for (ChatConversationModel conversation : conversations) {
            if (!CoreChatService.ChatConversationStatus.ACTIVE.equals(conversation.getStatus())) {
                continue;
            }
            if (chatEventStreamRegistry.isClientOnline(conversation.getUuid())) {
                continue;
            }
            if (conversation.getLastMessageAt() != null && conversation.getLastMessageAt().after(cutoff)) {
                continue;
            }
            coreChatService.updateConversationStatus(conversation.getId(),
                    CoreChatService.ChatConversationStatus.IDLE);
            chatEventPublisher.publishPresence(conversation.getUuid(), false);
            parked++;
        }
        if (parked > 0) {
            log.info("Parked {} chat conversations as IDLE (no live client stream for {} minutes)",
                    parked, idleAfterMinutes);
        }
    }
}
