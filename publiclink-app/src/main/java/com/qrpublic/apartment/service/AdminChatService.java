package com.qrpublic.apartment.service;

import com.qrpublic.apartment.chat.convertor.ChatConvertor;
import com.qrpublic.apartment.chat.request.AdminChatReplyRequest;
import com.qrpublic.apartment.chat.request.ChatTypingRequest;
import com.qrpublic.apartment.chat.response.AdminConversationListResponse;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatHistoryResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.core.model.ChatConversationModel;
import com.qrpublic.apartment.core.model.ChatMessageModel;
import com.qrpublic.apartment.domain.chat.ChatConversationDomain;
import com.qrpublic.apartment.domain.chat.ChatEventPublisher;
import com.qrpublic.apartment.domain.chat.ChatMessageDomain;
import com.qrpublic.apartment.domain.chat.ZaloBridgeDomain;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Administrator console (Mode B) of the chat bridge: the inbox the support team can
 * answer from a browser instead of the Zalo app.
 *
 * Access is enforced by the API gateway: these endpoints live under /admin/v1/chat,
 * which is not in the gateway permit-all list, so an authenticated Admin JWT is
 * required to reach them. The service itself deliberately re-checks nothing, matching
 * how AdminController already behaves.
 */
@Slf4j
@Service
public class AdminChatService {

    private static final String SOURCE_ADMIN = "ADMIN";

    @Autowired
    PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    ChatConversationDomain chatConversationDomain;

    @Autowired
    ChatMessageDomain chatMessageDomain;

    @Autowired
    ChatEventPublisher chatEventPublisher;

    @Autowired
    ZaloBridgeDomain zaloBridgeDomain;

    @Value("${chat.history-limit:500}")
    private int historyLimit;

    @Value("${chat.max-message-length:2000}")
    private int maxMessageLength;

    /** Inbox page: every conversation with counters, plus live connection totals. */
    public Result<AdminConversationListResponse> listConversations(int page, int size) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, AdminConversationListResponse>() {
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
                List<ChatConversationResponse> items = conversations.stream()
                        .map(AdminChatService::toResponse)
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
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, ChatHistoryResponse>() {
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
     * Answers from the console. The visitor receives it immediately over their event
     * stream; when the Zalo bridge is on, the same text is mirrored into the Zalo
     * thread so nothing answered elsewhere is missing there.
     */
    public Result<ChatMessageResponse> reply(AdminChatReplyRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<AdminChatReplyRequest, ChatMessageResponse>() {
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
        return publicLinkServiceTemplate.execute(new ProcessCallback<ChatTypingRequest, Void>() {
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
                chatMessageDomain.publishTyping(conversation.getDeviceId(), conversation.getUuid(),
                        SOURCE_ADMIN, Boolean.TRUE.equals(request.getTyping()));
                return null;
            }
        });
    }

    /** Ends a thread; the history remains for the visitor's next return. */
    public Result<ChatConversationResponse> close(String conversationUuid) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, ChatConversationResponse>() {
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

    /** Live feed of the whole inbox: new messages, status changes, presence, typing. */
    public Flux<ServerSentEvent<String>> stream() {
        return chatEventPublisher.adminStream();
    }

    private ChatConversationModel findConversation(String uuid) {
        return chatConversationDomain.findByUuid(uuid)
                .orElseThrow(() -> new ApplicationException("Conversation not found: " + uuid, 404));
    }

    private static ChatConversationResponse toResponse(ChatConversationModel conversation) {
        return ChatConvertor.toConversationResponse(conversation);
    }
}
