package com.qrpublic.apartment.chat.domain;

import com.qrpublic.apartment.chat.core.model.ChatConversationModel;
import com.qrpublic.apartment.chat.core.model.ChatDeviceModel;
import com.qrpublic.apartment.chat.core.service.CoreChatService;
import com.qrpublic.apartment.chat.util.ChatIdentityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the cross-account chat leak: two signed-in accounts on
 * one browser share a device id, and the old device-first identity merged their
 * threads. Identity must be account-first, and the anonymous ladder must never
 * reach an account's conversation.
 */
@ExtendWith(MockitoExtension.class)
class ChatIdentityDomainTest {

        @Mock
        CoreChatService coreChatService;

        @InjectMocks
        ChatIdentityDomain domain;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(domain, "identitySalt", "test-salt");
                ReflectionTestUtils.setField(domain, "fingerprintThreshold", 0.8d);
                ReflectionTestUtils.setField(domain, "partialThreshold", 0.5d);
                ReflectionTestUtils.setField(domain, "candidateLimit", 200);
        }

        private ChatDeviceModel device(String id) {
                ChatDeviceModel model = new ChatDeviceModel();
                model.setDeviceId(id);
                model.setVisitCount(1);
                return model;
        }

        private ChatConversationModel conversation(String uuid, String owner) {
                ChatConversationModel model = new ChatConversationModel();
                model.setId(1L);
                model.setUuid(uuid);
                model.setDeviceId("shared-device");
                model.setStatus("ACTIVE");
                model.setOwnerUsername(owner);
                return model;
        }

        @Test
        void signedInAccountResumesItsOwnThreadEvenOnASharedDevice() {
                when(coreChatService.findDeviceByDeviceId("shared-device"))
                                .thenReturn(Optional.of(device("shared-device")));
                when(coreChatService.registerVisit(eq("shared-device"), any(), any(), any()))
                                .thenReturn(device("shared-device"));
                when(coreChatService.findLatestConversationByOwner("micro08"))
                                .thenReturn(Optional.of(conversation("conv-08", "micro08")));

                ChatIdentityResult result = domain.identify("shared-device", List.of(), "1.2.3.4", "ua", "micro08",
                                "Micro Eight");

                assertThat(result.getConversation().getUuid()).isEqualTo("conv-08");
                assertThat(result.getRecognition()).isEqualTo("ACCOUNT");
                assertThat(result.isResumed()).isTrue();
                verify(coreChatService, never()).createConversation(anyString(), any(), any());
        }

        @Test
        void secondAccountOnTheSameDeviceGetsItsOwnThread() {
                when(coreChatService.findDeviceByDeviceId("shared-device"))
                                .thenReturn(Optional.of(device("shared-device")));
                when(coreChatService.registerVisit(eq("shared-device"), any(), any(), any()))
                                .thenReturn(device("shared-device"));
                when(coreChatService.findLatestConversationByOwner("micro08"))
                                .thenReturn(Optional.of(conversation("conv-08", "micro08")));
                when(coreChatService.findLatestConversationByOwner("micro09")).thenReturn(Optional.empty());
                when(coreChatService.createConversation(eq("shared-device"), eq("micro09"), eq("Micro Nine")))
                                .thenReturn(conversation("conv-09", "micro09"));

                ChatIdentityResult eight = domain.identify("shared-device", List.of(), "1.2.3.4", "ua", "micro08",
                                "Micro Eight");
                ChatIdentityResult nine = domain.identify("shared-device", List.of(), "1.2.3.4", "ua", "micro09",
                                "Micro Nine");

                assertThat(eight.getConversation().getUuid()).isEqualTo("conv-08");
                assertThat(nine.getConversation().getUuid()).isEqualTo("conv-09");
                assertThat(nine.getConversation().getUuid()).isNotEqualTo(eight.getConversation().getUuid());
        }

        @Test
        void anonymousVisitorNeverInheritsAnAccountThread() {
                when(coreChatService.findDeviceByDeviceId("shared-device"))
                                .thenReturn(Optional.of(device("shared-device")));
                when(coreChatService.registerVisit(eq("shared-device"), any(), any(), any()))
                                .thenReturn(device("shared-device"));
                // The device has an account thread, but the anonymous lookup must not see it.
                when(coreChatService.findLatestAnonymousConversationByDeviceId("shared-device"))
                                .thenReturn(Optional.empty());
                when(coreChatService.createConversation(eq("shared-device"), isNull(), isNull()))
                                .thenReturn(conversation("conv-anon", null));

                ChatIdentityResult result = domain.identify("shared-device", List.of(), "1.2.3.4", "ua", null, null);

                assertThat(result.getConversation().getOwnerUsername()).isNull();
                assertThat(result.getConversation().getUuid()).isEqualTo("conv-anon");
                verify(coreChatService, never()).findLatestConversationByOwner(anyString());
                verify(coreChatService, never()).findLatestConversationByDeviceId(anyString());
        }

        @Test
        void anonymousFingerprintNeverAdoptsAnAccountDevice() {
                // Same browser attributes as a signed-in account's device: the anonymous
                // ladder must skip that device entirely instead of adopting its row and
                // overwriting its fingerprint, IP hash and visit counter.
                String fingerprint = ChatIdentityUtils.buildFingerprintToken(List.of("ua", "en"), "test-salt");
                ChatDeviceModel accountDevice = device("account-device");
                accountDevice.setFingerprint(fingerprint);
                when(coreChatService.listDeviceCandidates(200)).thenReturn(List.of(accountDevice));
                when(coreChatService.deviceHasOwnedConversation("account-device")).thenReturn(true);
                when(coreChatService.registerVisit(anyString(), any(), any(), any()))
                                .thenAnswer(invocation -> device(invocation.getArgument(0)));
                when(coreChatService.findLatestAnonymousConversationByDeviceId(anyString()))
                                .thenReturn(Optional.empty());
                when(coreChatService.createConversation(anyString(), isNull(), isNull()))
                                .thenAnswer(invocation -> conversation("conv-anon", null));

                ChatIdentityResult result = domain.identify(null, List.of("ua", "en"), "1.2.3.4", "ua", null, null);

                assertThat(result.getDevice().getDeviceId()).isNotEqualTo("account-device");
                assertThat(result.getConversation().getOwnerUsername()).isNull();
                verify(coreChatService).deviceHasOwnedConversation("account-device");
        }
}
