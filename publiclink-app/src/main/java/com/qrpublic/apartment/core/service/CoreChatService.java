package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.ChatConversationModel;
import com.qrpublic.apartment.core.model.ChatDeviceModel;
import com.qrpublic.apartment.core.model.ChatDirectionEnum;
import com.qrpublic.apartment.core.model.ChatMessageModel;
import com.qrpublic.apartment.core.model.ChatStatusEnum;
import com.qrpublic.apartment.entity.ChatConversation;
import com.qrpublic.apartment.entity.ChatDevice;
import com.qrpublic.apartment.entity.ChatMessage;
import com.qrpublic.apartment.repository.ChatConversationRepository;
import com.qrpublic.apartment.repository.ChatDeviceRepository;
import com.qrpublic.apartment.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core layer for the chat feature: the only place that touches chat entities.
 * Everything above this layer works with core models, never with JPA entities.
 */
@Slf4j
@Service
public class CoreChatService {

    @Autowired
    ChatDeviceRepository chatDeviceRepository;

    @Autowired
    ChatConversationRepository chatConversationRepository;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    // ------------------------------------------------------------------ device

    @Transactional(readOnly = true)
    public Optional<ChatDeviceModel> findDeviceByDeviceId(String deviceId) {
        return chatDeviceRepository.findByDeviceId(deviceId).map(this::toModel);
    }

    @Transactional(readOnly = true)
    public List<ChatDeviceModel> findDevicesByFingerprint(String fingerprint) {
        return chatDeviceRepository.findByFingerprintOrderByLastSeenDesc(fingerprint).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatDeviceModel> findDevicesByIpHash(String ipHash) {
        return chatDeviceRepository.findByIpHashOrderByLastSeenDesc(ipHash).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatDeviceModel> listDeviceCandidates(int limit) {
        return chatDeviceRepository.findByFingerprintIsNotNullOrderByLastSeenDesc(PageRequest.of(0, limit)).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    /**
     * Record a visit. Creates the device on first sight, otherwise bumps the
     * visit counter and refreshes the volatile attributes (ip / agent / fingerprint).
     */
    @Transactional
    public ChatDeviceModel registerVisit(String deviceId, String fingerprint, String ipHash, String userAgent) {
        Timestamp now = Timestamp.from(Instant.now());
        ChatDevice device = chatDeviceRepository.findByDeviceId(deviceId).orElse(null);

        if (device == null) {
            device = new ChatDevice();
            device.setDeviceId(deviceId);
            device.setVisitCount(1);
            device.setFirstSeen(now);
        } else {
            device.setVisitCount(device.getVisitCount() == null ? 1 : device.getVisitCount() + 1);
        }

        if (fingerprint != null && !fingerprint.isBlank()) {
            device.setFingerprint(fingerprint);
        }
        if (ipHash != null && !ipHash.isBlank()) {
            device.setIpHash(ipHash);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            device.setUserAgent(truncate(userAgent, 300));
        }
        device.setLastSeen(now);

        ChatDevice saved = chatDeviceRepository.save(device);
        log.info("Chat device registered: deviceId={}, visits={}", saved.getDeviceId(), saved.getVisitCount());
        return toModel(saved);
    }

    // ----------------------------------------------------------- conversation

    @Transactional
    public ChatConversationModel createConversation(String deviceId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setUuid(UUID.randomUUID().toString());
        conversation.setDeviceId(deviceId);
        conversation.setStatus(ChatConversationStatus.ACTIVE);
        conversation.setCreatedAt(Timestamp.from(Instant.now()));
        conversation.setUpdatedAt(Timestamp.from(Instant.now()));
        ChatConversation saved = chatConversationRepository.save(conversation);

        // The tag is derived from the primary key, so it can only be set after insert.
        saved.setClientTag(buildClientTag(saved.getId()));
        return toModel(chatConversationRepository.save(saved), false);
    }

    @Transactional(readOnly = true)
    public Optional<ChatConversationModel> findConversationByUuid(String uuid) {
        return chatConversationRepository.findByUuid(uuid).map(c -> toModel(c, false));
    }

    @Transactional(readOnly = true)
    public Optional<ChatConversationModel> findConversationById(Long id) {
        return chatConversationRepository.findById(id).map(c -> toModel(c, false));
    }

    /** Conversation that owns a client tag; the tag is only unique per visit window. */
    @Transactional(readOnly = true)
    public Optional<ChatConversationModel> findConversationByClientTag(String clientTag) {
        if (clientTag == null || clientTag.isBlank()) {
            return Optional.empty();
        }
        return chatConversationRepository.findTop1ByClientTagOrderByIdDesc(clientTag.trim())
                .map(c -> toModel(c, false));
    }

    /** Newest conversation of a device — a returning visitor resumes this one. */
    @Transactional(readOnly = true)
    public Optional<ChatConversationModel> findLatestConversationByDeviceId(String deviceId) {
        List<ChatConversation> list = chatConversationRepository.findByDeviceIdOrderByIdDesc(deviceId);
        return list.isEmpty() ? Optional.empty() : Optional.of(toModel(list.get(0), false));
    }

    @Transactional(readOnly = true)
    public List<ChatConversationModel> listConversations(int page, int size) {
        return chatConversationRepository
                .findByStatusIsNotNullOrderByLastMessageAtDesc(PageRequest.of(page, size))
                .getContent().stream()
                .map(c -> toModel(c, true))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countConversations() {
        return chatConversationRepository.count();
    }

    @Transactional
    public Optional<ChatConversationModel> updateConversationStatus(Long conversationId, String status) {
        return chatConversationRepository.findById(conversationId).map(conversation -> {
            conversation.setStatus(status);
            conversation.setUpdatedAt(Timestamp.from(Instant.now()));
            return toModel(chatConversationRepository.save(conversation), false);
        });
    }

    /** Marks the conversation as just-active; keeps IDLE conversations resurrectable. */
    @Transactional
    public void touchConversation(Long conversationId) {
        chatConversationRepository.findById(conversationId).ifPresent(conversation -> {
            Timestamp now = Timestamp.from(Instant.now());
            conversation.setLastMessageAt(now);
            conversation.setUpdatedAt(now);
            conversation.setStatus(ChatConversationStatus.ACTIVE);
            chatConversationRepository.save(conversation);
        });
    }

    // --------------------------------------------------------------- message

    @Transactional
    public ChatMessageModel saveMessage(Long conversationId, ChatDirectionEnum direction, String content,
                                       String clientMessageId, Long quoteMessageId) {
        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setDirection(direction.name());
        message.setStatus(ChatStatusEnum.PENDING.name());
        message.setContent(truncate(content, 65000));
        message.setClientMessageId(clientMessageId);
        message.setQuoteMessageId(quoteMessageId);
        message.setCreatedAt(Timestamp.from(Instant.now()));

        ChatMessage saved = chatMessageRepository.save(message);
        touchConversation(conversationId);
        return toModel(saved);
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessageModel> findByClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return Optional.empty();
        }
        return chatMessageRepository.findByClientMessageId(clientMessageId).map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessageModel> findByZaloMessageId(String zaloMessageId) {
        if (zaloMessageId == null || zaloMessageId.isBlank()) {
            return Optional.empty();
        }
        return chatMessageRepository.findByZaloMessageId(zaloMessageId).map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessageModel> findMessageById(Long messageId) {
        return messageId == null ? Optional.empty() : chatMessageRepository.findById(messageId).map(this::toModel);
    }

    /** Messages already handed to Zalo — the set a broad "admin opened the chat" event applies to. */
    @Transactional(readOnly = true)
    public List<ChatMessageModel> listDispatchedMessages(ChatDirectionEnum direction, int limit) {
        return chatMessageRepository.findDispatchedMessages(direction.name(), PageRequest.of(0, limit)).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageModel> listMessages(Long conversationId) {
        return chatMessageRepository.listByConversation(conversationId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageModel> replayAfter(Long conversationId, Long afterId) {
        return chatMessageRepository.replayAfter(conversationId, afterId == null ? 0L : afterId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessageModel> findLastMessage(Long conversationId) {
        List<ChatMessage> last = chatMessageRepository.findLastOfConversation(conversationId, PageRequest.of(0, 1));
        return last.isEmpty() ? Optional.empty() : Optional.of(toModel(last.get(0)));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageModel> findRecentMessages(ChatDirectionEnum direction, int limit) {
        return chatMessageRepository.findRecentByDirection(direction.name(), PageRequest.of(0, limit)).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    /**
     * Move a message forward on the delivery ladder. Late or replayed Zalo events
     * (the platform retries a webhook up to five times) are ignored once a status
     * has been confirmed, so the client UI never goes backwards.
     */
    @Transactional
    public Optional<ChatMessageModel> advanceStatus(Long messageId, ChatStatusEnum target,
                                                    String zaloMessageId, Integer errorCode) {
        return chatMessageRepository.findById(messageId).map(message -> {
            ChatStatusEnum current = ChatStatusEnum.from(message.getStatus());

            if (zaloMessageId != null && !zaloMessageId.isBlank()) {
                message.setZaloMessageId(zaloMessageId);
            }
            if (errorCode != null) {
                message.setErrorCode(errorCode);
            }

            if (!current.isAtOrAfter(target)) {
                message.setStatus(target.name());
                Timestamp now = Timestamp.from(Instant.now());
                if (target == ChatStatusEnum.DELIVERED && message.getDeliveredAt() == null) {
                    message.setDeliveredAt(now);
                }
                if (target == ChatStatusEnum.SEEN && message.getSeenAt() == null) {
                    message.setSeenAt(now);
                }
            }
            message.setUpdatedAt(now_());
            return toModel(chatMessageRepository.save(message));
        });
    }

    // ------------------------------------------------------------- conversion

    private String buildClientTag(Long conversationId) {
        return String.format("%04d", Math.abs(conversationId % 10000));
    }

    private static Timestamp now_() {
        return Timestamp.from(Instant.now());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private ChatDeviceModel toModel(ChatDevice device) {
        ChatDeviceModel model = new ChatDeviceModel();
        model.setId(device.getId());
        model.setDeviceId(device.getDeviceId());
        model.setFingerprint(device.getFingerprint());
        model.setIpHash(device.getIpHash());
        model.setUserAgent(device.getUserAgent());
        model.setVisitCount(device.getVisitCount());
        model.setFirstSeen(device.getFirstSeen());
        model.setLastSeen(device.getLastSeen());
        return model;
    }

    private ChatConversationModel toModel(ChatConversation conversation, boolean withCounters) {
        ChatConversationModel model = new ChatConversationModel();
        model.setId(conversation.getId());
        model.setUuid(conversation.getUuid());
        model.setDeviceId(conversation.getDeviceId());
        model.setStatus(conversation.getStatus());
        model.setClientTag(conversation.getClientTag());
        model.setCreatedAt(conversation.getCreatedAt());
        model.setLastMessageAt(conversation.getLastMessageAt());
        if (withCounters) {
            model.setClientMessageCount(
                    chatMessageRepository.countByConversationAndDirection(conversation.getId(),
                            ChatDirectionEnum.CLIENT.name()));
            model.setAdminMessageCount(
                    chatMessageRepository.countByConversationAndDirection(conversation.getId(),
                            ChatDirectionEnum.ADMIN.name()));
            List<ChatMessage> last = chatMessageRepository.findLastOfConversation(conversation.getId(),
                    PageRequest.of(0, 1));
            model.setAwaitingAdmin(!last.isEmpty()
                    && ChatDirectionEnum.CLIENT.name().equals(last.get(0).getDirection()));
        }
        return model;
    }

    private ChatMessageModel toModel(ChatMessage message) {
        ChatMessageModel model = new ChatMessageModel();
        model.setId(message.getId());
        model.setConversationId(message.getConversation() != null ? message.getConversation().getId() : null);
        model.setConversationUuid(message.getConversation() != null ? message.getConversation().getUuid() : null);
        model.setDeviceId(message.getConversation() != null ? message.getConversation().getDeviceId() : null);
        model.setClientTag(message.getConversation() != null ? message.getConversation().getClientTag() : null);
        model.setClientMessageId(message.getClientMessageId());
        model.setDirection(ChatDirectionEnum.valueOf(message.getDirection()));
        model.setStatus(ChatStatusEnum.from(message.getStatus()));
        model.setContent(message.getContent());
        model.setZaloMessageId(message.getZaloMessageId());
        model.setQuoteMessageId(message.getQuoteMessageId());
        model.setErrorCode(message.getErrorCode());
        model.setCreatedAt(message.getCreatedAt());
        model.setDeliveredAt(message.getDeliveredAt());
        model.setSeenAt(message.getSeenAt());
        return model;
    }

    /** Conversation status values (kept as plain strings in the schema). */
    public static final class ChatConversationStatus {
        private ChatConversationStatus() {
        }

        public static final String ACTIVE = "ACTIVE";
        public static final String IDLE = "IDLE";
        public static final String CLOSED = "CLOSED";
    }
}
