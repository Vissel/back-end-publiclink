package com.qrpublic.apartment.chat.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.chat.convertor.ChatConvertor;
import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Turns domain facts into server-sent events for the two audiences of the
 * bridge:
 * the client chat window (device scoped) and the administrator console
 * (shared).
 *
 * Event names consumed by the frontend: {@code message}, {@code status},
 * {@code typing}, {@code conversation}, {@code ping}.
 */
@Slf4j
@Component
public class ChatEventPublisher {

    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_TYPING = "typing";
    public static final String EVENT_CONVERSATION = "conversation";
    public static final String EVENT_PRESENCE = "presence";
    public static final String EVENT_PING = "ping";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    ChatEventStreamRegistry chatEventStreamRegistry;

    /** Raw event stream of one conversation; consumed by the client's SSE endpoint. */
    public Flux<ServerSentEvent<String>> streamFor(String conversationUuid) {
        return chatEventStreamRegistry.clientStream(conversationUuid);
    }

    /** Raw event stream of the administrator console. */
    public Flux<ServerSentEvent<String>> adminStream() {
        return chatEventStreamRegistry.adminStream();
    }

    /**
     * A new message body: the client that caused it and the admin console both see
     * it.
     */
    public void publishMessage(ChatMessageModel message) {
        ChatMessageResponse payload = ChatConvertor.toMessageResponse(message);
        String json = write(payload);
        if (json == null) {
            return;
        }
        chatEventStreamRegistry.publishToClient(message.getConversationUuid(), event(EVENT_MESSAGE, json, message.getId()));
        chatEventStreamRegistry.publishToAdmins(event(EVENT_MESSAGE, json, message.getId()));
    }

    /**
     * A status transition only: the client re-renders the tick icons of one
     * message.
     */
    public void publishStatus(ChatMessageModel message) {
        ChatMessageResponse payload = ChatConvertor.toMessageResponse(message);
        String json = write(payload);
        if (json == null) {
            return;
        }
        chatEventStreamRegistry.publishToClient(message.getConversationUuid(), event(EVENT_STATUS, json, message.getId()));
        chatEventStreamRegistry.publishToAdmins(event(EVENT_STATUS, json, message.getId()));
    }

    /**
     * Typing indicator. Only the administrator console can produce it — Zalo does
     * not expose a typing signal, so a reply written in the Zalo app appears as a
     * message without a preceding typing hint.
     */
    public void publishTyping(String conversationUuid, String source, boolean typing) {
        String json = write(new TypingPayload(conversationUuid, source, typing));
        if (json == null) {
            return;
        }
        chatEventStreamRegistry.publishToClient(conversationUuid, event(EVENT_TYPING, json, null));
        chatEventStreamRegistry.publishToAdmins(event(EVENT_TYPING, json, null));
    }

    /** Keeps the administrator inbox list fresh without a page reload. */
    public void publishConversation(ChatConversationModel conversation) {
        ChatConversationResponse payload = ChatConvertor.toConversationResponse(conversation);
        String json = write(payload);
        if (json == null) {
            return;
        }
        chatEventStreamRegistry.publishToAdmins(event(EVENT_CONVERSATION, json, conversation.getId()));
    }

    /**
     * Connection state of one visitor. Tells the administrator that the client
     * lost the system (offline) or came back (online) without waiting for a
     * message.
     */
    public void publishPresence(String conversationUuid, boolean online) {
        String json = write(new PresencePayload(conversationUuid, online));
        if (json == null) {
            return;
        }
        log.info("Chat client {} for conversation {}", online ? "returned" : "disconnected", conversationUuid);
        chatEventStreamRegistry.publishToAdmins(event(EVENT_PRESENCE, json, null));
    }

    public void publishPing(String deviceId) {
        chatEventStreamRegistry.publishToClient(deviceId, event(EVENT_PING, "1", null));
    }

    /**
     * Keeps the administrator console stream warm. An idle SSE connection is
     * what makes an intermediary (nginx, the tunnel) close the response, and the
     * console would then silently stop receiving guest messages until reload.
     */
    public void publishAdminPing() {
        if (chatEventStreamRegistry.onlineAdminCount() == 0) {
            return;
        }
        chatEventStreamRegistry.publishToAdmins(event(EVENT_PING, "1", null));
    }

    public boolean isClientOnline(String deviceId) {
        return chatEventStreamRegistry.isClientOnline(deviceId);
    }

    public int onlineClientCount() {
        return chatEventStreamRegistry.onlineClientCount();
    }

    public int onlineAdminCount() {
        return chatEventStreamRegistry.onlineAdminCount();
    }

    private ServerSentEvent<String> event(String name, String data, Long id) {
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder(data).event(name);
        return id == null ? builder.build() : builder.id(String.valueOf(id)).build();
    }

    private String write(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Unable to serialise chat event payload: {}", e.getMessage());
            return null;
        }
    }

    /** Typing frame: who is typing, in which conversation. */
    private record TypingPayload(String conversationUuid, String source, boolean typing) {
    }

    /** Presence frame: whether the visitor's stream is currently open. */
    private record PresencePayload(String conversationUuid, boolean online) {
    }
}
