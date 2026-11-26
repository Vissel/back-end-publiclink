package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.BadmintonCourtScheduleModel;
import com.qrpublic.apartment.entity.BadmintonBooking;
import com.qrpublic.apartment.entity.BadmintonCourt;
import com.qrpublic.apartment.entity.BadmintonCourtSchedule;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.repository.BadmintonBookingRepository;
import com.qrpublic.apartment.repository.BadmintonCourtRepository;
import com.qrpublic.apartment.repository.BadmintonCourtScheduleRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreBadmintonServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7); // Monday

    @Mock
    private BadmintonCourtRepository courtRepository;
    @Mock
    private BadmintonCourtScheduleRepository scheduleRepository;
    @Mock
    private BadmintonBookingRepository bookingRepository;
    @Mock
    private SaleEnvironmentRepository saleEnvironmentRepository;
    @InjectMocks
    private CoreBadmintonService service;

    @Test
    void rejectsSeventhCourt() {
        BadmintonCourtModel draft = draft();
        when(courtRepository.countBySaleEnvironmentEnvId("env-1")).thenReturn(6L);

        assertThatThrownBy(() -> service.createCourt("env-1", draft))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("at most 6")
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(400));
        verify(saleEnvironmentRepository, never()).findById(any());
        verify(courtRepository, never()).save(any());
    }

    @Test
    void rejectsAnyOverlappingNonClosedBooking() {
        BadmintonCourt court = court();
        BadmintonCourtSchedule schedule = schedule(court);
        BadmintonBooking existing = booking(court, schedule);
        when(courtRepository.findByCourtCodeForUpdate("court-1")).thenReturn(Optional.of(court));
        when(scheduleRepository.findByCourtCourtIdAndDayOfWeek(1L, 1)).thenReturn(Optional.of(schedule));
        when(bookingRepository.findConflicting(1L, DATE, LocalTime.of(8, 0), LocalTime.of(9, 0)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.book("court-1", DATE, "08:00", "buyer", "Buyer", null))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("already been booked")
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(409));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void snapshotsSchedulePriceWhenBooking() {
        BadmintonCourt court = court();
        BadmintonCourtSchedule schedule = schedule(court);
        when(courtRepository.findByCourtCodeForUpdate("court-1")).thenReturn(Optional.of(court));
        when(scheduleRepository.findByCourtCourtIdAndDayOfWeek(1L, 1)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(any(BadmintonBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BadmintonBookingModel result = service.book("court-1", DATE, "08:00", "buyer", "Buyer", null);

        ArgumentCaptor<BadmintonBooking> captor = ArgumentCaptor.forClass(BadmintonBooking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getPriceVnd()).isEqualByComparingTo("150000.00");
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.getStatus()).isEqualTo(CoreBadmintonService.PENDING_PAYMENT);
        assertThat(result.getBuyerUsername()).isEqualTo("buyer");
    }

    @Test
    void enforcesPendingPaymentWaitingConfirmConfirmedTransitions() {
        BadmintonCourt court = court();
        BadmintonBooking booking = booking(court, schedule(court));
        when(bookingRepository.findByBookingCodeForUpdate("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(BadmintonBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.paymentConfirm("booking-1").getStatus()).isEqualTo(CoreBadmintonService.WAITING_CONFIRM);
        assertThat(service.confirm("booking-1").getStatus()).isEqualTo(CoreBadmintonService.CONFIRMED);
        assertThatThrownBy(() -> service.cancel("booking-1"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(409));
    }

    @Test
    void allowsCancelFromWaitingConfirmAndRejectWithNote() {
        BadmintonCourt court = court();
        BadmintonBooking booking = booking(court, schedule(court));
        booking.setStatus(CoreBadmintonService.WAITING_CONFIRM);
        when(bookingRepository.findByBookingCodeForUpdate("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(BadmintonBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.reject("booking-1", "Court unavailable").getStatus())
                .isEqualTo(CoreBadmintonService.REJECTED);
        assertThat(booking.getNote()).isEqualTo("Court unavailable");

        booking.setStatus(CoreBadmintonService.WAITING_CONFIRM);
        assertThat(service.cancel("booking-1").getStatus()).isEqualTo(CoreBadmintonService.CANCELLED);
    }

    @Test
    void rejectsInvalidFrameStart() {
        BadmintonCourt court = court();
        when(courtRepository.findByCourtCodeForUpdate("court-1")).thenReturn(Optional.of(court));
        when(scheduleRepository.findByCourtCourtIdAndDayOfWeek(1L, 1))
                .thenReturn(Optional.of(schedule(court)));

        assertThatThrownBy(() -> service.book("court-1", DATE, "08:30", "buyer", "Buyer", null))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("not a valid court frame");
        verify(bookingRepository, never()).save(any());
    }

    private BadmintonCourtModel draft() {
        return BadmintonCourtModel.builder()
                .courtName("Court 1")
                .schedules(List.of(BadmintonCourtScheduleModel.builder()
                        .dayOfWeek(1)
                        .openTime("08:00")
                        .closeTime("10:00")
                        .slotMinutes(60)
                        .priceVnd(new BigDecimal("150000.00"))
                        .build()))
                .build();
    }

    private BadmintonCourt court() {
        SaleEnvironment env = new SaleEnvironment();
        env.setEnvId("env-1");
        BadmintonCourt court = new BadmintonCourt();
        court.setCourtId(1L);
        court.setCourtCode("court-1");
        court.setCourtName("Court 1");
        court.setSaleEnvironment(env);
        court.setActive(true);
        return court;
    }

    private BadmintonCourtSchedule schedule(BadmintonCourt court) {
        BadmintonCourtSchedule schedule = new BadmintonCourtSchedule();
        schedule.setScheduleId(10L);
        schedule.setCourt(court);
        schedule.setDayOfWeek(1);
        schedule.setOpenTime(LocalTime.of(8, 0));
        schedule.setCloseTime(LocalTime.of(10, 0));
        schedule.setSlotMinutes(60);
        schedule.setPriceVnd(new BigDecimal("150000.00"));
        return schedule;
    }

    private BadmintonBooking booking(BadmintonCourt court, BadmintonCourtSchedule schedule) {
        BadmintonBooking booking = new BadmintonBooking();
        booking.setBookingId(1L);
        booking.setBookingCode("booking-1");
        booking.setCourt(court);
        booking.setSchedule(schedule);
        booking.setBookingDate(DATE);
        booking.setStartTime(LocalTime.of(8, 0));
        booking.setEndTime(LocalTime.of(9, 0));
        booking.setBuyerUsername("buyer");
        booking.setBuyerName("Buyer");
        booking.setPriceVnd(schedule.getPriceVnd());
        booking.setStatus(CoreBadmintonService.PENDING_PAYMENT);
        return booking;
    }
}
