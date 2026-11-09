package com.qrpublic.apartment.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.chat.convertor.ChatConvertor;
import com.qrpublic.apartment.chat.request.ChatHistoryRequest;
import com.qrpublic.apartment.chat.request.ChatSendMessageRequest;
import com.qrpublic.apartment.chat.request.ChatStartRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.response.ChatHealthResponse;
import com.qrpublic.apartment.chat.response.ChatStartResponse;
import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatDeviceModel;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.domain.ChatConversationDomain;
import com.qrpublic.apartment.chat.domain.ChatEventPublisher;
import com.qrpublic.apartment.chat.domain.ChatIdentityDomain;
import com.qrpublic.apartment.chat.domain.ChatIdentityResult;
import com.qrpublic.apartment.chat.domain.ChatMessageDomain;
import com.qrpublic.apartment.chat.domain.ZaloBridgeDomain;
import com.qrpublic.apartment.chat.exception.ApplicationException;
import com.qrpublic.apartment.chat.template.model.Result;
import com.qrpublic.apartment.chat.template.service.ProcessCallback;
import com.qrpublic.apartment.chat.template.service.ChatServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer of the chat feature. Every operation runs through
 * {@link ChatServiceTemplate}: validation in preProcess, work in process,
 * failures converted into the shared {@link Result} error contract.
 *
 * The one exception is {@link #attachStream}, which is a long-lived reactive
 * publisher rather than a request/response call.
 */
@Slf4j
@Service
public class ChatService {

    private static final String SOURCE_CLIENT = "CLIENT";

    private static final ObjectMapper STREAM_MAPPER = new ObjectMapper();

    @Autowired
    ChatServiceTemplate chatServiceTemplate;

    @Autowired
    ChatIdentityDomain chatIdentityDomain;

    @Autowired
    ChatConversationDomain chatConversationDomain;

    @Autowired
    ChatMessageDomain chatMessageDomain;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    @Autowired
    ZaloBridgeDomain zaloBridgeDomain;

    @Value("${chat.max-message-length:2000}")
    private int maxMessageLength;

    @Value("${chat.history-limit:500}")
    private int historyLimit;

    @Value("${chat.heartbeat-seconds:20}")
    private long heartbeatSeconds;

    /**
     * Opens a conversation, or resumes the one this device already has. Answers
     * with
     * the full history so a returning visitor continues exactly where they left
     * off.
     */
    public Result<ChatStartResponse> start(ChatStartRequest request, String clientIp, String userAgent) {
        return chatServiceTemplate.execute(new ProcessCallback<ChatStartRequest, ChatStartResponse>() {
            @Override
            public ChatStartRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChatStartRequest req) {
                Assert.notNull(req, "Chat start request is required.");
                Assert.isTrue(req.getDeviceId() == null || req.getDeviceId().length() <= 64,
                        "Device id is too long.");
            }

            @Override
            public ChatStartResponse process() {
                ChatIdentityResult identity = chatIdentityDomain.identify(
                        request.getDeviceId(), request.getAttributes(), clientIp, userAgent);
                ChatDeviceModel device = identity.getDevice();
                ChatConversationModel conversation = identity.getConversation();

                List<ChatMessageModel> messages = chatMessageDomain.listMessages(conversation.getId()).stream()
                        .limit(historyLimit)
                        .toList();

                ChatStartResponse response = new ChatStartResponse();
                response.setDeviceId(device.getDeviceId());
                response.setRecognition(identity.getRecognition());
                response.setResumed(identity.isResumed());
                response.setVisitCount(device.getVisitCount());
                response.setConversation(ChatConvertor.toConversationResponse(conversation,
                        identity.getRecognition(), device.getVisitCount(),
                        messages.isEmpty() ? null : messages.get(messages.size() - 1)));
                response.setMessages(messages.stream().map(ChatConvertor::toMessageResponse).toList());
                response.setServerTime(Instant.now().toString());

                log.info("Chat session started: conversation={}, recognition={}, resumed={}, history={}",
                        conversation.getUuid(), identity.getRecognition(), identity.isResumed(), messages.size());
                return response;
            }
        });
    }

    /**
     * Records a visitor message and mirrors it into the administrator's Zalo
     * thread.
     */
    public Result<ChatMessageResponse> sendMessage(ChatSendMessageRequest request) {
        return chatServiceTemplate.execute(new ProcessCallback<ChatSendMessageRequest, ChatMessageResponse>() {
            @Override
            public ChatSendMessageRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChatSendMessageRequest req) {
                Assert.notNull(req, "Chat message request is required.");
                Assert.hasText(req.getContent(), "Message content is required.");
                Assert.isTrue(req.getContent().length() <= maxMessageLength,
                        "Message is longer than " + maxMessageLength + " characters.");
                Assert.isTrue(req.getConversationUuid() != null && !req.getConversationUuid().isBlank()
                        || req.getDeviceId() != null && !req.getDeviceId().isBlank(),
                        "Either conversationUuid or deviceId is required.");
            }

            @Override
            public ChatMessageResponse process() {
                ChatConversationModel conversation = resolveConversation(
                        request.getConversationUuid(), request.getDeviceId());
                ChatMessageModel saved = chatMessageDomain.saveClientMessage(
                        conversation.getId(), request.getClientMessageId(), request.getContent());
                return ChatConvertor.toMessageResponse(saved);
            }
        });
    }

    /**
     * Reads history. A plain read (no afterMessageId) also marks the
     * administrator's
     * messages as seen, which is the client-side twin of the Zalo seen receipt.
     */
    public Result<ChatHistoryResponse> history(ChatHistoryRequest request) {
        return chatServiceTemplate.execute(new ProcessCallback<ChatHistoryRequest, ChatHistoryResponse>() {
            @Override
            public ChatHistoryRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChatHistoryRequest req) {
                Assert.notNull(req, "Chat history request is required.");
                Assert.hasText(req.getConversationUuid(), "Conversation uuid is required.");
            }

            @Override
            public ChatHistoryResponse process() {
                ChatConversationModel conversation = chatConversationDomain.findByUuid(request.getConversationUuid())
                        .orElseThrow(() -> new ApplicationException("Conversation not found.", 404));

                boolean catchUp = request.getAfterMessageId() != null;
                List<ChatMessageModel> messages = catchUp
                        ? chatMessageDomain.replay(conversation.getId(), request.getAfterMessageId())
                        : chatMessageDomain.listMessages(conversation.getId());

                if (!catchUp) {
                    chatMessageDomain.markAdminMessagesSeenByClient(conversation.getId());
                }

                ChatHistoryResponse response = new ChatHistoryResponse();
                response.setConversationUuid(conversation.getUuid());
                response.setMessages(messages.stream().limit(historyLimit)
                        .map(ChatConvertor::toMessageResponse).toList());
                response.setLastMessageId(messages.isEmpty() ? request.getAfterMessageId()
                        : messages.get(messages.size() - 1).getId());
                response.setComplete(true);
                return response;
            }
        });
    }

    /** Visitor typing indicator, relayed to the open administrator console. */
    public Result<Void> typing(ChatTypingRequest request) {
        return chatServiceTemplate.execute(new ProcessCallback<ChatTypingRequest, Void>() {
            @Override
            public ChatTypingRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChatTypingRequest req) {
                Assert.notNull(req, "Typing request is required.");
                Assert.notNull(req.getTyping(), "Typing flag is required.");
            }

            @Override
            public Void process() {
                ChatConversationModel conversation = resolveConversationQuietly(
                        request.getConversationUuid(), request.getDeviceId());
                if (conversation == null) {
                    return null;
                }
                chatMessageDomain.publishTyping(conversation.getDeviceId(), conversation.getUuid(),
                        SOURCE_CLIENT, Boolean.TRUE.equals(request.getTyping()));
                return null;
            }
        });
    }

    /** Visitor explicitly ends the thread; history is kept for the next return. */
    public Result<ChatConversationResponse> closeConversation(String conversationUuid) {
        return chatServiceTemplate.execute(new ProcessCallback<String, ChatConversationResponse>() {
            @Override
            public String getRequest() {
                return conversationUuid;
            }

            @Override
            public void preProcess(String uuid) {
                Assert.hasText(uuid, "Conversation uuid is required.");
            }

            @Override
            public ChatConversationResponse process() {
                ChatConversationModel conversation = chatConversationDomain.findByUuid(conversationUuid)
                        .orElseThrow(() -> new ApplicationException("Conversation not found.", 404));
                return ChatConvertor.toConversationResponse(
                        chatConversationDomain.close(conversation.getId()).orElse(conversation));
            }
        });
    }

    /**
     * Liveness probe for deployment scripts and load balancers. Answers 200 with a
     * tiny payload and reports whether the Zalo leg is live or the bridge is running
     * console-only, which is the first thing a broken release check needs to know.
     */
    public Result<ChatHealthResponse> health() {
        return chatServiceTemplate.execute(new ProcessCallback<Void, ChatHealthResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // nothing to validate
            }

            @Override
            public ChatHealthResponse process() {
                return new ChatHealthResponse("UP", "chat-app", "/chat", zaloBridgeDomain.isBridgeEnabled());
            }
        });
    }

    /**
     * Live event stream of one visitor: a hello frame, the messages missed since
     * {@code lastEventId}, then the live feed with a heartbeat that keeps proxies
     * from closing an otherwise idle connection.
     *
     * Blocking work (conversation lookup, replay read) runs on the bounded elastic
     * scheduler, never on the event-loop thread serving the request.
     */
    public Flux<ServerSentEvent<String>> attachStream(String deviceId, String conversationUuid, Long lastEventId) {
        return Mono.fromCallable(() -> openStreamContext(deviceId, conversationUuid))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(context -> {
                    ServerSentEvent<String> hello = ServerSentEvent
                            .builder("{\"conversationUuid\":\"" + context.conversation().getUuid()
                                    + "\",\"deviceId\":\"" + context.deviceId() + "\"}")
                            .event("hello")
                            .build();

                    Flux<ServerSentEvent<String>> replay = Flux.fromIterable(context.replay());
                    Flux<ServerSentEvent<String>> live = chatEventPublisher.streamFor(context.deviceId())
                            .filter(event -> isNewerThanWatermark(event, context.watermark()));
                    Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(heartbeatSeconds))
                            .map(tick -> ServerSentEvent.builder("").event("ping").build());

                    return Flux.concat(Mono.just(hello), replay,
                            Flux.merge(live, heartbeat))
                            .doFinally(signal -> chatConversationDomain.onClientDisconnected(
                                    context.deviceId(), context.conversation().getUuid()));
                });
    }

    private StreamContext openStreamContext(String deviceId, String conversationUuid) {
        ChatConversationModel conversation = resolveConversation(conversationUuid, deviceId);
        chatConversationDomain.onClientConnected(conversation);

        List<ChatMessageModel> missed = chatMessageDomain.replay(conversation.getId(), null);
        List<ServerSentEvent<String>> frames = missed.stream()
                .limit(historyLimit)
                .map(message -> ServerSentEvent
                        .builder(toJson(message))
                        .event("message")
                        .id(String.valueOf(message.getId()))
                        .build())
                .toList();
        long watermark = missed.isEmpty() ? 0L : missed.get(missed.size() - 1).getId();
        return new StreamContext(conversation, conversation.getDeviceId(), frames, watermark);
    }

    private String toJson(ChatMessageModel message) {
        try {
            return STREAM_MAPPER.writeValueAsString(ChatConvertor.toMessageResponse(message));
        } catch (Exception e) {
            log.error("Unable to serialise replayed chat message {}: {}", message.getId(), e.getMessage());
            return "{}";
        }
    }

    private static boolean isNewerThanWatermark(ServerSentEvent<String> event, long watermark) {
        String id = event.id();
        if (id == null || watermark <= 0) {
            // Frames without a message id (typing, ping, presence) always pass.
            return true;
        }
        try {
            return Long.parseLong(id) > watermark;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private ChatConversationModel resolveConversation(String uuid, String deviceId) {
        if (uuid != null && !uuid.isBlank()) {
            return chatConversationDomain.findByUuid(uuid)
                    .orElseThrow(() -> new ApplicationException("Conversation not found.", 404));
        }
        return chatConversationDomain.findLatestByDeviceId(deviceId)
                .orElseThrow(() -> new ApplicationException(
                        "No chat conversation for this device. Call /start first.", 404));
    }

    private ChatConversationModel resolveConversationQuietly(String uuid, String deviceId) {
        Optional<ChatConversationModel> found = uuid != null && !uuid.isBlank()
                ? chatConversationDomain.findByUuid(uuid)
                : chatConversationDomain.findLatestByDeviceId(deviceId);
        return found.orElse(null);
    }

    /** Everything the opened stream needs, resolved once off the event loop. */
    private record StreamContext(ChatConversationModel conversation, String deviceId,
            List<ServerSentEvent<String>> replay, long watermark) {
    }
}
