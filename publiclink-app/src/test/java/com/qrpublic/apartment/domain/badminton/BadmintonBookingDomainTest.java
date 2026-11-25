package com.qrpublic.apartment.domain.badminton;

import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreBadmintonService;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.integration.ChatMessageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadmintonBookingDomainTest {
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7);

    @Mock
    private BadmintonEnvAccess badmintonEnvAccess;
    @Mock
    private CoreBadmintonService coreBadmintonService;
    @Mock
    private CoreUserService coreUserService;
    @Mock
    private CoreNotificationService coreNotificationService;
    @Mock
    private ChatMessageClient chatMessageClient;
    @InjectMocks
    private BadmintonBookingDomain domain;

    @Test
    void rejectsCourtFromAnotherEnvironment() {
        YogaEnvModel env = env("env-1", "seller");
        when(badmintonEnvAccess.resolveForBooking("token")).thenReturn(env);
        when(badmintonEnvAccess.dbToday()).thenReturn(DATE.minusDays(1));
        when(coreBadmintonService.findByCode("other-court"))
                .thenReturn(BadmintonCourtModel.builder().courtCode("other-court").envId("env-2").build());

        assertThatThrownBy(() -> domain.book("token", "other-court", DATE, "08:00", "buyer"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("does not belong")
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(403));
        verify(coreBadmintonService, never()).book(any(), any(), any(), any(), any(), any());
    }

    @Test
    void paymentConfirmNotifiesEnvironmentSeller() {
        YogaEnvModel env = env("env-1", "seller");
        BadmintonBookingModel pending = booking(CoreBadmintonService.PENDING_PAYMENT);
        BadmintonBookingModel waiting = booking(CoreBadmintonService.WAITING_CONFIRM);
        when(badmintonEnvAccess.resolveForBooking("token")).thenReturn(env);
        when(coreBadmintonService.findBookingByCode("booking-1")).thenReturn(pending);
        when(coreBadmintonService.findByCode("court-1")).thenReturn(court("env-1"));
        when(coreBadmintonService.paymentConfirm("booking-1")).thenReturn(waiting);

        assertThat(domain.paymentConfirm("token", "booking-1", "buyer").getStatus())
                .isEqualTo(CoreBadmintonService.WAITING_CONFIRM);
        verify(coreNotificationService).notifyUsernames(eq(List.of("seller")), anyString(), anyString(),
                eq("SELLER"), eq("buyer"), eq("COURT_BOOKING"), eq("booking-1"));
    }

    @Test
    void sellerConfirmStillSucceedsWhenNotificationAndChatFail() {
        YogaEnvModel env = env("env-1", "seller");
        BadmintonBookingModel waiting = booking(CoreBadmintonService.WAITING_CONFIRM);
        BadmintonBookingModel confirmed = booking(CoreBadmintonService.CONFIRMED);
        when(badmintonEnvAccess.resolveForSeller("token", "seller")).thenReturn(env);
        when(coreBadmintonService.findBookingByCode("booking-1")).thenReturn(waiting);
        when(coreBadmintonService.findByCode("court-1")).thenReturn(court("env-1"));
        when(coreBadmintonService.confirm("booking-1")).thenReturn(confirmed);
        doThrow(new IllegalStateException("notification unavailable")).when(coreNotificationService)
                .notifyUsernames(anyList(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(chatMessageClient.sendSystemMessage(anyString(), anyString()))
                .thenThrow(new IllegalStateException("chat unavailable"));

        BadmintonBookingModel result = domain.confirm("token", "seller", "booking-1");

        assertThat(result.getStatus()).isEqualTo(CoreBadmintonService.CONFIRMED);
        verify(coreNotificationService).notifyUsernames(eq(List.of("buyer")), anyString(), anyString(),
                eq("BUYER"), eq("seller"), eq("COURT_BOOKING"), eq("booking-1"));
        verify(chatMessageClient).sendSystemMessage(eq("buyer"), anyString());
    }

    @Test
    void sellerRejectStillSucceedsWhenNotificationAndChatFail() {
        YogaEnvModel env = env("env-1", "seller");
        BadmintonBookingModel waiting = booking(CoreBadmintonService.WAITING_CONFIRM);
        BadmintonBookingModel rejected = booking(CoreBadmintonService.REJECTED);
        when(badmintonEnvAccess.resolveForSeller("token", "seller")).thenReturn(env);
        when(coreBadmintonService.findBookingByCode("booking-1")).thenReturn(waiting);
        when(coreBadmintonService.findByCode("court-1")).thenReturn(court("env-1"));
        when(coreBadmintonService.reject("booking-1", "Court unavailable")).thenReturn(rejected);
        doThrow(new IllegalStateException("notification unavailable")).when(coreNotificationService)
                .notifyUsernames(anyList(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(chatMessageClient.sendSystemMessage(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("chat down")));

        BadmintonBookingModel result = domain.reject("token", "seller", "booking-1", "Court unavailable");

        assertThat(result.getStatus()).isEqualTo(CoreBadmintonService.REJECTED);
        verify(chatMessageClient).sendSystemMessage(eq("buyer"), anyString());
    }

    private YogaEnvModel env(String envId, String seller) {
        return YogaEnvModel.builder().envId(envId).sellerUsername(seller).active(true).build();
    }

    private BadmintonCourtModel court(String envId) {
        return BadmintonCourtModel.builder().courtCode("court-1").courtName("Court 1").envId(envId).active(true).build();
    }

    private BadmintonBookingModel booking(String status) {
        return BadmintonBookingModel.builder()
                .bookingCode("booking-1")
                .courtCode("court-1")
                .courtName("Court 1")
                .date(DATE.toString())
                .startTime("08:00")
                .endTime("09:00")
                .priceVnd(new BigDecimal("150000.00"))
                .status(status)
                .buyerUsername("buyer")
                .buyerName("Buyer")
                .build();
    }
}
