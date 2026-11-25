package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatDeviceModel;
import com.qrpublic.apartment.chat.core.model.ChatDirectionEnum;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.core.model.ChatStatusEnum;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageDomainSystemMessageTest {

    @Mock
    CoreChatService coreChatService;

    @Mock
    ZaloBridgeDomain zaloBridgeDomain;

    @Mock
    ChatEventPublisher chatEventPublisher;

    @Mock
    ChatConversationDomain chatConversationDomain;

    @Mock
    AdminPushDomain adminPushDomain;

    @InjectMocks
    ChatMessageDomain domain;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(domain, "maxMessageLength", 2000);
    }

    @Test
    void appendsSentAdminMessageToLatestOpenConversationAndPublishesUnreadState() {
        ChatConversationModel existing = conversation(1L, "conv-existing", "ACTIVE", "browser-device");
        ChatMessageModel pending = message(10L, existing, ChatStatusEnum.PENDING);
        ChatMessageModel sent = message(10L, existing, ChatStatusEnum.SENT);
        ChatConversationModel refreshed = conversation(1L, "conv-existing", "ACTIVE", "browser-device");
        refreshed.setUnreadCount(3);

        when(coreChatService.findLatestConversationByOwner("player01"))
                .thenReturn(Optional.of(existing));
        when(coreChatService.saveMessage(1L, ChatDirectionEnum.ADMIN, "Booking confirmed.", null, null))
                .thenReturn(pending);
        when(coreChatService.advanceStatus(10L, ChatStatusEnum.SENT, null, null))
                .thenReturn(Optional.of(sent));
        when(chatConversationDomain.markUnread(1L)).thenReturn(Optional.of(refreshed));

        ChatMessageModel result = domain.saveSystemMessage(" player01 ", " Booking confirmed. ");

        assertThat(result).isSameAs(sent);
        assertThat(result.getStatus()).isEqualTo(ChatStatusEnum.SENT);
        verify(coreChatService, never()).registerVisit(anyString(), any(), any(), any());
        verify(coreChatService, never()).createConversation(anyString(), any(), any());

        var order = inOrder(coreChatService, chatEventPublisher, chatConversationDomain);
        order.verify(coreChatService).findLatestConversationByOwner("player01");
        order.verify(coreChatService).saveMessage(1L, ChatDirectionEnum.ADMIN, "Booking confirmed.", null, null);
        order.verify(coreChatService).advanceStatus(10L, ChatStatusEnum.SENT, null, null);
        order.verify(chatEventPublisher).publishMessage(sent);
        order.verify(chatConversationDomain).markUnread(1L);
        order.verify(chatEventPublisher).publishConversation(refreshed);
        verifyNoInteractions(zaloBridgeDomain, adminPushDomain);
    }

    @ParameterizedTest
    @MethodSource("missingOrClosedLatestConversation")
    void createsSyntheticConversationWhenLatestIsMissingOrClosed(
            Optional<ChatConversationModel> latest) {
        when(coreChatService.findLatestConversationByOwner("player01")).thenReturn(latest);
        when(coreChatService.registerVisit(anyString(), isNull(), isNull(), eq("publiclink-system")))
                .thenAnswer(invocation -> {
                    ChatDeviceModel device = new ChatDeviceModel();
                    device.setDeviceId(invocation.getArgument(0));
                    device.setVisitCount(1);
                    return device;
                });
        when(coreChatService.createConversation(anyString(), eq("player01"), eq("player01")))
                .thenAnswer(invocation -> conversation(2L, "conv-new", "ACTIVE", invocation.getArgument(0)));

        ChatConversationModel created = conversation(2L, "conv-new", "ACTIVE", "pending-device");
        ChatMessageModel pending = message(20L, created, ChatStatusEnum.PENDING);
        ChatMessageModel sent = message(20L, created, ChatStatusEnum.SENT);
        ChatConversationModel refreshed = conversation(2L, "conv-new", "ACTIVE", "pending-device");
        refreshed.setUnreadCount(1);
        when(coreChatService.saveMessage(2L, ChatDirectionEnum.ADMIN, "Booking rejected.", null, null))
                .thenReturn(pending);
        when(coreChatService.advanceStatus(20L, ChatStatusEnum.SENT, null, null))
                .thenReturn(Optional.of(sent));
        when(chatConversationDomain.markUnread(2L)).thenReturn(Optional.of(refreshed));

        ChatMessageModel result = domain.saveSystemMessage("player01", "Booking rejected.");

        ArgumentCaptor<String> deviceId = ArgumentCaptor.forClass(String.class);
        verify(coreChatService).registerVisit(deviceId.capture(), isNull(), isNull(), eq("publiclink-system"));
        assertThat(deviceId.getValue()).startsWith("system-").hasSize(43);
        verify(coreChatService).createConversation(deviceId.getValue(), "player01", "player01");
        verify(coreChatService).saveMessage(2L, ChatDirectionEnum.ADMIN, "Booking rejected.", null, null);
        verify(coreChatService).advanceStatus(20L, ChatStatusEnum.SENT, null, null);
        verify(chatEventPublisher).publishMessage(sent);
        verify(chatEventPublisher).publishConversation(refreshed);
        assertThat(result).isSameAs(sent);
        verifyNoInteractions(zaloBridgeDomain, adminPushDomain);
    }

    static Stream<Optional<ChatConversationModel>> missingOrClosedLatestConversation() {
        return Stream.of(
                Optional.empty(),
                Optional.of(conversation(99L, "conv-closed", "CLOSED", "old-device")));
    }

    private static ChatConversationModel conversation(Long id, String uuid, String status, String deviceId) {
        ChatConversationModel conversation = new ChatConversationModel();
        conversation.setId(id);
        conversation.setUuid(uuid);
        conversation.setStatus(status);
        conversation.setDeviceId(deviceId);
        conversation.setOwnerUsername("player01");
        return conversation;
    }

    private static ChatMessageModel message(Long id, ChatConversationModel conversation, ChatStatusEnum status) {
        ChatMessageModel message = new ChatMessageModel();
        message.setId(id);
        message.setConversationId(conversation.getId());
        message.setConversationUuid(conversation.getUuid());
        message.setDirection(ChatDirectionEnum.ADMIN);
        message.setStatus(status);
        return message;
    }
}
