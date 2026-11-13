package com.qrpublic.apartment.chat.service;

import com.qrpublic.apartment.chat.convertor.ChatConvertor;
import com.qrpublic.apartment.chat.request.AdminChatReplyRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.request.PushSubscriptionRequest;
import com.qrpublic.apartment.chat.response.AdminConversationListResponse;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.response.StreamTicketResponse;
import com.qrpublic.apartment.chat.response.VapidPublicKeyResponse;
import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.domain.ChatConversationDomain;
import com.qrpublic.apartment.chat.domain.AdminPushDomain;
import com.qrpublic.apartment.chat.domain.ChatEventPublisher;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Administrator console (Mode B) of the chat bridge: the inbox the support team
 * can
 * answer from a browser instead of the Zalo app.
 *
 * Access is enforced by the API gateway: these endpoints live under
 * /admin/v1/chat,
 * which is not in the gateway permit-all list, so an authenticated Admin JWT is
 * required to reach them. The service itself deliberately re-checks nothing,
 * matching
 * how AdminController already behaves.
 */
@Slf4j
@Service
public class AdminChatService {

    private static final String SOURCE_ADMIN = "ADMIN";

    @Autowired
    ChatServiceTemplate chatServiceTemplate;

    @Autowired
    ChatConversationDomain chatConversationDomain;

    @Autowired
    ChatMessageDomain chatMessageDomain;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    @Autowired
    ZaloBridgeDomain zaloBridgeDomain;

    @Autowired
    AdminPushDomain adminPushDomain;

    @Value("${chat.history-limit:500}")
    private int historyLimit;

    @Value("${chat.max-message-length:2000}")
    private int maxMessageLength;

    /** How long a stream ticket survives before it is useless. */
    @Value("${chat.stream-ticket-ttl-seconds:60}")
    private long streamTicketTtlSeconds;

    /**
     * Issued tickets: token to expiry instant in millis. In memory on purpose -
     * a restart only forces the console to ask for a new ticket, and a ticket
     * that outlived a restart would be a credential nobody can revoke.
     */
    private final Map<String, Long> streamTickets = new ConcurrentHashMap<>();

    /**
     * Inbox page: every conversation with counters, plus live connection totals.
     */
    public Result<AdminConversationListResponse> listConversations(int page, int size) {
        return chatServiceTemplate.execute(new ProcessCallback<Void, AdminConversationListResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.isTrue(page >= 0, "Page must not be negative.");
                Assert.isTrue(size > 0 && size <= 200, "Page size must be between 1 and 200.");
            }

            @Override
            public AdminConversationListResponse process() {
                List<ChatConversationModel> conversations = chatConversationDomain.listForAdmin(page, size);
                // The preview needs the last message of each thread; the inbox is
                // small and this call now runs on load and on reconnect only, not
                // on a four-second timer.
                List<ChatConversationResponse> items = conversations.stream()
                        .map(conversation -> ChatConvertor.toConversationResponse(conversation, null, null,
                                chatMessageDomain.lastMessage(conversation.getId()).orElse(null)))
                        .toList();

                AdminConversationListResponse response = new AdminConversationListResponse();
                response.setConversations(items);
                response.setTotal(chatConversationDomain.countConversations());
                response.setPage(page);
                response.setSize(size);
                response.setOnlineClients(chatEventPublisher.onlineClientCount());
                response.setOnlineAdmins(chatEventPublisher.onlineAdminCount());
                response.setZaloEnabled(zaloBridgeDomain.isBridgeEnabled());
                return response;
            }
        });
    }

    /** Full thread of one conversation. */
    public Result<ChatHistoryResponse> history(String conversationUuid) {
        return chatServiceTemplate.execute(new ProcessCallback<String, ChatHistoryResponse>() {
            @Override
            public String getRequest() {
                return conversationUuid;
            }

            @Override
            public void preProcess(String uuid) {
                Assert.hasText(uuid, "Conversation uuid is required.");
            }

            @Override
            public ChatHistoryResponse process() {
                ChatConversationModel conversation = findConversation(conversationUuid);
                List<ChatMessageModel> messages = chatMessageDomain.listMessages(conversation.getId());

                // Opening the thread is what marks it read, WhatsApp style; every
                // console then drops the badge for this conversation.
                chatConversationDomain.markRead(conversation.getId())
                        .ifPresent(chatEventPublisher::publishConversation);

                ChatHistoryResponse response = new ChatHistoryResponse();
                response.setConversationUuid(conversation.getUuid());
                response.setMessages(messages.stream().limit(historyLimit)
                        .map(ChatConvertor::toMessageResponse).toList());
                response.setLastMessageId(messages.isEmpty() ? null : messages.get(messages.size() - 1).getId());
                response.setComplete(true);
                return response;
            }
        });
    }

    /**
     * Answers from the console. The visitor receives it immediately over their
     * event
     * stream; when the Zalo bridge is on, the same text is mirrored into the Zalo
     * thread so nothing answered elsewhere is missing there.
     */
    public Result<ChatMessageResponse> reply(AdminChatReplyRequest request) {
        return chatServiceTemplate.execute(new ProcessCallback<AdminChatReplyRequest, ChatMessageResponse>() {
            @Override
            public AdminChatReplyRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(AdminChatReplyRequest req) {
                Assert.notNull(req, "Reply request is required.");
                Assert.hasText(req.getConversationUuid(), "Conversation uuid is required.");
                Assert.hasText(req.getContent(), "Reply content is required.");
                Assert.isTrue(req.getContent().length() <= maxMessageLength,
                        "Reply is longer than " + maxMessageLength + " characters.");
            }

            @Override
            public ChatMessageResponse process() {
                ChatConversationModel conversation = findConversation(request.getConversationUuid());
                ChatMessageModel reply = chatMessageDomain.consoleReply(
                        conversation.getId(), request.getContent(), conversation.getClientTag());
                log.info("Console reply added to conversation {} by administrator", conversation.getUuid());
                return ChatConvertor.toMessageResponse(reply);
            }
        });
    }

    /** Relays "administrator is typing…" to the waiting visitor. */
    public Result<Void> typing(ChatTypingRequest request) {
        return chatServiceTemplate.execute(new ProcessCallback<ChatTypingRequest, Void>() {
            @Override
            public ChatTypingRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(ChatTypingRequest req) {
                Assert.notNull(req, "Typing request is required.");
                Assert.hasText(req.getConversationUuid(), "Conversation uuid is required.");
                Assert.notNull(req.getTyping(), "Typing flag is required.");
            }

            @Override
            public Void process() {
                ChatConversationModel conversation = findConversation(request.getConversationUuid());
                chatMessageDomain.publishTyping(conversation.getUuid(),
                        SOURCE_ADMIN, Boolean.TRUE.equals(request.getTyping()));
                return null;
            }
        });
    }

    /** Ends a thread; the history remains for the visitor's next return. */
    public Result<ChatConversationResponse> close(String conversationUuid) {
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
                ChatConversationModel conversation = findConversation(conversationUuid);
                return ChatConvertor.toConversationResponse(
                        chatConversationDomain.close(conversation.getId()).orElse(conversation));
            }
        });
    }

    /**
     * Live feed of the whole inbox: new messages, status changes, presence, typing.
     */
    public Flux<ServerSentEvent<String>> stream() {
        return chatEventPublisher.adminStream();
    }

    /**
     * Hands out the single-use credential for the event stream. The call itself
     * is JWT-guarded by the gateway, which is what makes the ticket meaningful.
     */
    public Result<StreamTicketResponse> issueStreamTicket() {
        return chatServiceTemplate.execute(new ProcessCallback<Void, StreamTicketResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // No input to validate: the caller is identified by the JWT.
            }

            @Override
            public StreamTicketResponse process() {
                purgeExpiredTickets();
                String ticket = UUID.randomUUID().toString();
                streamTickets.put(ticket, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(streamTicketTtlSeconds));
                log.info("Issued a chat stream ticket, {} outstanding", streamTickets.size());
                return new StreamTicketResponse(ticket, streamTicketTtlSeconds);
            }
        });
    }

    /**
     * Consumes a ticket: true for the first presentation of a live one only.
     * The stream route is the sole exception to gateway JWT protection, so this
     * check is the authentication of that endpoint.
     */
    public boolean authorizeStream(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        Long expiry = streamTickets.remove(ticket);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    private void purgeExpiredTickets() {
        long now = System.currentTimeMillis();
        streamTickets.values().removeIf(expiry -> expiry <= now);
    }

    /**
     * Registers this browser for background notifications. The administrator is
     * the gateway-forwarded username, never a value from the body, so a browser
     * cannot subscribe somebody else's account.
     */
    public Result<Void> subscribePush(PushSubscriptionRequest request, String adminUsername) {
        return chatServiceTemplate.execute(new ProcessCallback<PushSubscriptionRequest, Void>() {
            @Override
            public PushSubscriptionRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(PushSubscriptionRequest req) {
                Assert.notNull(req, "Subscription is required.");
                Assert.hasText(req.getEndpoint(), "Push endpoint is required.");
                Assert.notNull(req.getKeys(), "Push subscription keys are required.");
                Assert.hasText(adminUsername, "An authenticated administrator is required.");
            }

            @Override
            public Void process() {
                adminPushDomain.subscribe(adminUsername, request.getEndpoint(),
                        request.getKeys().getP256dh(), request.getKeys().getAuth());
                return null;
            }
        });
    }

    /** The browser unsubscribed itself (permission revoked, profile cleared). */
    public Result<Void> unsubscribePush(String endpoint) {
        return chatServiceTemplate.execute(new ProcessCallback<String, Void>() {
            @Override
            public String getRequest() {
                return endpoint;
            }

            @Override
            public void preProcess(String value) {
                Assert.hasText(value, "Push endpoint is required.");
            }

            @Override
            public Void process() {
                adminPushDomain.unsubscribe(endpoint);
                return null;
            }
        });
    }

    /** Lets the console decide whether to offer the notification switch at all. */
    public Result<VapidPublicKeyResponse> vapidPublicKey() {
        return chatServiceTemplate.execute(new ProcessCallback<Void, VapidPublicKeyResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // No input: this only reports server configuration.
            }

            @Override
            public VapidPublicKeyResponse process() {
                return new VapidPublicKeyResponse(adminPushDomain.getPublicKey(), adminPushDomain.isConfigured());
            }
        });
    }

    private ChatConversationModel findConversation(String uuid) {
        return chatConversationDomain.findByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Conversation not found: " + uuid, 404));
    }
}
