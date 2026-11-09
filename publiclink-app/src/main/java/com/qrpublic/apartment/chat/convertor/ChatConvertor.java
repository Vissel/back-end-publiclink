package com.qrpublic.apartment.chat.convertor;

import com.qrpublic.apartment.chat.response.ChatConversationResponse;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.core.model.ChatConversationModel;
import com.qrpublic.apartment.core.model.ChatMessageModel;

import java.sql.Timestamp;

/**
 * Core model to transport DTO conversion for the chat feature.
 */
public final class ChatConvertor {

    /** Preview length used in the administrator conversation list. */
    private static final int PREVIEW_LENGTH = 120;

    private ChatConvertor() {
    }

    public static ChatMessageResponse toMessageResponse(ChatMessageModel message) {
        if (message == null) {
            return null;
        }
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setConversationUuid(message.getConversationUuid());
        response.setDirection(message.getDirection() == null ? null : message.getDirection().name());
        response.setContent(message.getContent());
        response.setStatus(message.getStatus() == null ? null : message.getStatus().name());
        response.setClientMessageId(message.getClientMessageId());
        response.setCreatedAt(toIso(message.getCreatedAt()));
        response.setDeliveredAt(toIso(message.getDeliveredAt()));
        response.setSeenAt(toIso(message.getSeenAt()));
        response.setErrorCode(message.getErrorCode());
        response.setClientTag(message.getClientTag());
        return response;
    }

    public static ChatConversationResponse toConversationResponse(ChatConversationModel conversation) {
        return toConversationResponse(conversation, null, null, null);
    }

    public static ChatConversationResponse toConversationResponse(ChatConversationModel conversation,
                                                                 String recognition, Integer visitCount,
                                                                 ChatMessageModel lastMessage) {
        if (conversation == null) {
            return null;
        }
        ChatConversationResponse response = new ChatConversationResponse();
        response.setUuid(conversation.getUuid());
        response.setClientTag(conversation.getClientTag());
        response.setStatus(conversation.getStatus());
        response.setDeviceId(conversation.getDeviceId());
        response.setRecognition(recognition);
        response.setVisitCount(visitCount);
        response.setCreatedAt(toIso(conversation.getCreatedAt()));
        response.setLastMessageAt(toIso(conversation.getLastMessageAt()));
        response.setClientMessageCount(conversation.getClientMessageCount());
        response.setAdminMessageCount(conversation.getAdminMessageCount());
        response.setAwaitingAdmin(conversation.isAwaitingAdmin());
        response.setLastMessagePreview(preview(lastMessage));
        return response;
    }

    public static String preview(ChatMessageModel message) {
        if (message == null || message.getContent() == null) {
            return null;
        }
        String content = message.getContent().replaceAll("\\s+", " ").trim();
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "..." : content;
    }

    private static String toIso(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
