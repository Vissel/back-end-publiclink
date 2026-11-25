package com.qrpublic.apartment.chat.service;

import com.qrpublic.apartment.chat.core.model.ChatDirectionEnum;
import com.qrpublic.apartment.chat.core.model.ChatMessageModel;
import com.qrpublic.apartment.chat.core.model.ChatStatusEnum;
import com.qrpublic.apartment.chat.domain.ChatMessageDomain;
import com.qrpublic.apartment.chat.request.ChatSystemMessageRequest;
import com.qrpublic.apartment.chat.response.ChatMessageResponse;
import com.qrpublic.apartment.chat.template.model.Result;
import com.qrpublic.apartment.chat.template.service.ChatServiceTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceSystemMessageTest {

    @Mock
    ChatMessageDomain chatMessageDomain;

    @InjectMocks
    ChatService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chatServiceTemplate", new ChatServiceTemplate());
        ReflectionTestUtils.setField(service, "maxMessageLength", 2000);
    }

    @ParameterizedTest
    @MethodSource("blankSystemMessages")
    void rejectsBlankOwnerOrContentAsBadRequest(ChatSystemMessageRequest request) {
        Result<ChatMessageResponse> result = service.systemMessage(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).isNotBlank();
        verifyNoInteractions(chatMessageDomain);
    }

    @Test
    void returnsMessageDtoWithoutLeakingDomainEntities() {
        ChatMessageModel model = new ChatMessageModel();
        model.setId(30L);
        model.setConversationUuid("conv-player01");
        model.setDirection(ChatDirectionEnum.ADMIN);
        model.setStatus(ChatStatusEnum.SENT);
        model.setContent("Booking confirmed.");
        when(chatMessageDomain.saveSystemMessage("player01", "Booking confirmed.")).thenReturn(model);

        Result<ChatMessageResponse> result = service.systemMessage(
                new ChatSystemMessageRequest(" player01 ", "Booking confirmed."));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(30L);
        assertThat(result.getData().getConversationUuid()).isEqualTo("conv-player01");
        assertThat(result.getData().getDirection()).isEqualTo("ADMIN");
        assertThat(result.getData().getStatus()).isEqualTo("SENT");
        assertThat(result.getData().getContent()).isEqualTo("Booking confirmed.");
        verify(chatMessageDomain).saveSystemMessage("player01", "Booking confirmed.");
    }

    static Stream<ChatSystemMessageRequest> blankSystemMessages() {
        return Stream.of(
                new ChatSystemMessageRequest(null, "Booking confirmed."),
                new ChatSystemMessageRequest("", "Booking confirmed."),
                new ChatSystemMessageRequest("   ", "Booking confirmed."),
                new ChatSystemMessageRequest("player01", null),
                new ChatSystemMessageRequest("player01", ""),
                new ChatSystemMessageRequest("player01", "   "));
    }
}
