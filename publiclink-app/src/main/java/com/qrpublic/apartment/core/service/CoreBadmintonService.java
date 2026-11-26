package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.BadmintonCourtScheduleModel;
import com.qrpublic.apartment.core.model.BadmintonFrameModel;
import com.qrpublic.apartment.entity.BadmintonBooking;
import com.qrpublic.apartment.entity.BadmintonCourt;
import com.qrpublic.apartment.entity.BadmintonCourtSchedule;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.BadmintonBookingRepository;
import com.qrpublic.apartment.repository.BadmintonCourtRepository;
import com.qrpublic.apartment.repository.BadmintonCourtScheduleRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core persistence, frame generation and state machine for badminton bookings.
 */
@Slf4j
@Service
public class CoreBadmintonService {
    public static final int MAX_COURTS_PER_ENV = 6;
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String WAITING_CONFIRM = "WAITING_CONFIRM";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private BadmintonCourtRepository courtRepository;

    @Autowired
    private BadmintonCourtScheduleRepository scheduleRepository;

    @Autowired
    private BadmintonBookingRepository bookingRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Transactional
    public BadmintonCourtModel createCourt(String envId, BadmintonCourtModel draft) {
        validateCourt(draft);
        // Lock existing court rows before counting, serializing creators for an
        // environment that already has courts. The env_id index supplies the
        // InnoDB range lock when the first court is created concurrently.
        courtRepository.findAllByEnvIdForUpdate(envId);
        if (courtRepository.countBySaleEnvironmentEnvId(envId) >= MAX_COURTS_PER_ENV) {
            throw new ApplicationException("A badminton page can have at most 6 courts.", 400);
        }
        SaleEnvironment env = saleEnvironmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));
        BadmintonCourt entity = new BadmintonCourt();
        entity.setCourtCode(UUID.randomUUID().toString());
        entity.setSaleEnvironment(env);
        entity.setCourtName(draft.getCourtName().trim());
        entity.setActive(true);
        BadmintonCourt saved = courtRepository.save(entity);
        replaceSchedules(saved, draft.getSchedules());
        return toCourtModel(saved);
    }

    @Transactional
    public BadmintonCourtModel updateCourt(String courtCode, BadmintonCourtModel draft) {
        validateCourt(draft);
        BadmintonCourt entity = getCourtForUpdate(courtCode);
        entity.setCourtName(draft.getCourtName().trim());
        if (draft.getActive() != null) {
            entity.setActive(draft.getActive());
        }
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        BadmintonCourt saved = courtRepository.save(entity);
        replaceSchedules(saved, draft.getSchedules());
        return toCourtModel(saved);
    }

    @Transactional
    public void deactivateCourt(String courtCode) {
        BadmintonCourt entity = getCourtForUpdate(courtCode);
        entity.setActive(false);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        courtRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<BadmintonCourtModel> findCourts(String envId, boolean includeInactive) {
        List<BadmintonCourt> courts = includeInactive
                ? courtRepository.findBySaleEnvironmentEnvIdOrderByCourtCodeAsc(envId)
                : courtRepository.findBySaleEnvironmentEnvIdAndActiveTrueOrderByCourtCodeAsc(envId);
        return courts.stream().map(this::toCourtModel).toList();
    }

    @Transactional(readOnly = true)
    public BadmintonCourtModel findByCode(String courtCode) {
        return toCourtModel(getCourt(courtCode));
    }

    /** Split the selected weekday's schedule into complete fixed-length frames. */
    public List<BadmintonFrameModel> generateFrames(BadmintonCourtModel court, LocalDate date) {
        if (court == null || date == null || !Boolean.TRUE.equals(court.getActive())) {
            return List.of();
        }
        BadmintonCourtScheduleModel schedule = court.getSchedules().stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek().getValue())
                .findFirst().orElse(null);
        if (schedule == null) {
            return List.of();
        }
        LocalTime open = parseTime(schedule.getOpenTime());
        LocalTime close = parseTime(schedule.getCloseTime());
        if (open == null || close == null || schedule.getSlotMinutes() <= 0) {
            return List.of();
        }
        List<BadmintonFrameModel> frames = new ArrayList<>();
        for (LocalTime start = open; !start.plusMinutes(schedule.getSlotMinutes()).isAfter(close); start = start
                .plusMinutes(schedule.getSlotMinutes())) {
            LocalTime end = start.plusMinutes(schedule.getSlotMinutes());
            frames.add(BadmintonFrameModel.builder()
                    .startTime(format(start))
                    .endTime(format(end))
                    .priceVnd(schedule.getPriceVnd())
                    .state(BadmintonFrameModel.FREE)
                    .build());
        }
        return frames;
    }

    /**
     * Reserve one frame. The court write lock covers conflict SELECT + INSERT,
     * and every status except CANCELLED/REJECTED blocks the frame.
     */
    @Transactional
    public BadmintonBookingModel book(String courtCode, LocalDate date, String startTime,
            String buyerUsername, String buyerName, String note) {
        if (buyerUsername == null || buyerUsername.isBlank()) {
            throw new ApplicationException("Please sign in", 401);
        }
        if (date == null || startTime == null || startTime.isBlank()) {
            throw new ApplicationException("A booking date and start time are required.", 400);
        }
        BadmintonCourt court = getCourtForUpdate(courtCode);
        if (!court.isActive()) {
            throw new ApplicationException("This court is no longer active.", 400);
        }
        BadmintonCourtSchedule schedule = scheduleRepository
                .findByCourtCourtIdAndDayOfWeek(court.getCourtId(), date.getDayOfWeek().getValue())
                .orElseThrow(() -> new ApplicationException("This court has no schedule on the selected date.", 400));
        LocalTime start = parseTime(startTime);
        if (start == null || !isFrameStart(schedule, start)) {
            throw new ApplicationException("The selected start time is not a valid court frame.", 400);
        }
        LocalTime end = start.plusMinutes(schedule.getSlotMinutes());
        List<BadmintonBooking> conflicts = bookingRepository.findConflicting(
                court.getCourtId(), date, start, end);
        if (!conflicts.isEmpty()) {
            throw new ApplicationException("This court frame has already been booked.", 409);
        }

        BadmintonBooking entity = new BadmintonBooking();
        entity.setBookingCode(UUID.randomUUID().toString());
        entity.setCourt(court);
        entity.setSchedule(schedule);
        entity.setBookingDate(date);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setBuyerUsername(buyerUsername.trim());
        entity.setBuyerName(cleanBuyerName(buyerName));
        entity.setPriceVnd(schedule.getPriceVnd());
        entity.setStatus(PENDING_PAYMENT);
        entity.setNote(cleanNote(note));
        BadmintonBooking saved = bookingRepository.save(entity);
        log.info("Created badminton booking [{}] for court [{}] on [{}] {}",
                saved.getBookingCode(), courtCode, date, format(start));
        return toBookingModel(saved);
    }

    @Transactional
    public BadmintonBookingModel paymentConfirm(String bookingCode) {
        BadmintonBooking entity = getBookingForUpdate(bookingCode);
        requireStatus(entity, PENDING_PAYMENT);
        transition(entity, WAITING_CONFIRM);
        return toBookingModel(bookingRepository.save(entity));
    }

    @Transactional
    public BadmintonBookingModel cancel(String bookingCode) {
        BadmintonBooking entity = getBookingForUpdate(bookingCode);
        if (!PENDING_PAYMENT.equals(entity.getStatus()) && !WAITING_CONFIRM.equals(entity.getStatus())) {
            throw new ApplicationException("Only pending-payment or waiting-confirm bookings can be cancelled.", 409);
        }
        transition(entity, CANCELLED);
        return toBookingModel(bookingRepository.save(entity));
    }

    @Transactional
    public BadmintonBookingModel confirm(String bookingCode) {
        BadmintonBooking entity = getBookingForUpdate(bookingCode);
        requireStatus(entity, WAITING_CONFIRM);
        transition(entity, CONFIRMED);
        return toBookingModel(bookingRepository.save(entity));
    }

    @Transactional
    public BadmintonBookingModel reject(String bookingCode, String note) {
        BadmintonBooking entity = getBookingForUpdate(bookingCode);
        requireStatus(entity, WAITING_CONFIRM);
        entity.setNote(cleanNote(note));
        transition(entity, REJECTED);
        return toBookingModel(bookingRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public BadmintonBookingModel findBookingByCode(String bookingCode) {
        return toBookingModel(getBooking(bookingCode));
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> findBlockingByEnvAndDate(String envId, LocalDate date) {
        return bookingRepository.findBlockingByEnvAndDate(envId, date).stream()
                .map(this::toBookingModel).toList();
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> findByEnv(String envId) {
        return bookingRepository.findByEnvId(envId).stream().map(this::toBookingModel).toList();
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> findByEnvAndDate(String envId, LocalDate date) {
        return bookingRepository.findByEnvIdAndDate(envId, date).stream()
                .map(this::toBookingModel).toList();
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> findMyBookings(String envId, String buyerUsername) {
        if (buyerUsername == null || buyerUsername.isBlank()) {
            return List.of();
        }
        return bookingRepository.findByEnvIdAndBuyerUsername(envId, buyerUsername).stream()
                .map(this::toBookingModel).toList();
    }

    private void replaceSchedules(BadmintonCourt court, List<BadmintonCourtScheduleModel> schedules) {
        List<BadmintonCourtScheduleModel> supplied = schedules == null ? List.of() : schedules;
        validateSchedules(supplied);
        Map<Integer, BadmintonCourtSchedule> existing = new HashMap<>();
        for (BadmintonCourtSchedule schedule : scheduleRepository
                .findByCourtCourtIdOrderByDayOfWeekAsc(court.getCourtId())) {
            existing.put(schedule.getDayOfWeek(), schedule);
        }
        Set<Integer> suppliedDays = new HashSet<>();
        for (BadmintonCourtScheduleModel draft : supplied) {
            suppliedDays.add(draft.getDayOfWeek());
            BadmintonCourtSchedule entity = existing.get(draft.getDayOfWeek());
            if (entity == null) {
                entity = new BadmintonCourtSchedule();
                entity.setCourt(court);
            }
            entity.setDayOfWeek(draft.getDayOfWeek());
            entity.setOpenTime(parseTime(draft.getOpenTime()));
            entity.setCloseTime(parseTime(draft.getCloseTime()));
            entity.setSlotMinutes(draft.getSlotMinutes());
            entity.setPriceVnd(draft.getPriceVnd());
            scheduleRepository.save(entity);
        }
        for (BadmintonCourtSchedule omitted : existing.values()) {
            if (!suppliedDays.contains(omitted.getDayOfWeek())) {
                if (bookingRepository.existsByScheduleScheduleId(omitted.getScheduleId())) {
                    throw new ApplicationException(
                            "Cannot remove a weekly schedule that already has booking history.", 400);
                }
                scheduleRepository.delete(omitted);
            }
        }
    }

    private void validateCourt(BadmintonCourtModel draft) {
        if (draft == null || draft.getCourtName() == null || draft.getCourtName().isBlank()) {
            throw new ApplicationException("Court name is required.", 400);
        }
        if (draft.getCourtName().trim().length() > 100) {
            throw new ApplicationException("Court name may not exceed 100 characters.", 400);
        }
        validateSchedules(draft.getSchedules());
    }

    private void validateSchedules(List<BadmintonCourtScheduleModel> schedules) {
        if (schedules == null) {
            return;
        }
        Set<Integer> days = new HashSet<>();
        for (BadmintonCourtScheduleModel schedule : schedules) {
            if (schedule == null || schedule.getDayOfWeek() < 1 || schedule.getDayOfWeek() > 7) {
                throw new ApplicationException("dayOfWeek must be between 1 (Monday) and 7 (Sunday).", 400);
            }
            if (!days.add(schedule.getDayOfWeek())) {
                throw new ApplicationException("Only one schedule per court weekday is allowed.", 400);
            }
            LocalTime open = parseTime(schedule.getOpenTime());
            LocalTime close = parseTime(schedule.getCloseTime());
            if (open == null || close == null || !close.isAfter(open)) {
                throw new ApplicationException(
                        "Schedule open/close times must be valid HH:mm values with close after open.", 400);
            }
            if (schedule.getSlotMinutes() <= 0) {
                throw new ApplicationException("slotMinutes must be greater than zero.", 400);
            }
            if (schedule.getPriceVnd() == null || schedule.getPriceVnd().signum() < 0) {
                throw new ApplicationException("priceVnd must be zero or greater.", 400);
            }
        }
    }

    private boolean isFrameStart(BadmintonCourtSchedule schedule, LocalTime start) {
        LocalTime open = schedule.getOpenTime();
        LocalTime close = schedule.getCloseTime();
        long offset = Duration.between(open, start).toMinutes();
        return !start.isBefore(open)
                && offset % schedule.getSlotMinutes() == 0
                && !start.plusMinutes(schedule.getSlotMinutes()).isAfter(close);
    }

    private void requireStatus(BadmintonBooking entity, String expected) {
        if (!expected.equals(entity.getStatus())) {
            throw new ApplicationException("Booking must be " + expected + " for this operation.", 409);
        }
    }

    private void transition(BadmintonBooking entity, String status) {
        entity.setStatus(status);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
    }

    private BadmintonCourt getCourt(String courtCode) {
        return courtRepository.findByCourtCode(courtCode)
                .orElseThrow(() -> new ResourceNotFoundException("Badminton court not found: " + courtCode));
    }

    private BadmintonCourt getCourtForUpdate(String courtCode) {
        return courtRepository.findByCourtCodeForUpdate(courtCode)
                .orElseThrow(() -> new ResourceNotFoundException("Badminton court not found: " + courtCode));
    }

    private BadmintonBooking getBooking(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Badminton booking not found: " + bookingCode));
    }

    private BadmintonBooking getBookingForUpdate(String bookingCode) {
        return bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Badminton booking not found: " + bookingCode));
    }

    public BadmintonCourtModel toCourtModel(BadmintonCourt entity) {
        List<BadmintonCourtScheduleModel> schedules = scheduleRepository
                .findByCourtCourtIdOrderByDayOfWeekAsc(entity.getCourtId()).stream()
                .map(s -> BadmintonCourtScheduleModel.builder()
                        .dayOfWeek(s.getDayOfWeek())
                        .openTime(format(s.getOpenTime()))
                        .closeTime(format(s.getCloseTime()))
                        .slotMinutes(s.getSlotMinutes())
                        .priceVnd(s.getPriceVnd())
                        .build())
                .toList();
        return BadmintonCourtModel.builder()
                .courtCode(entity.getCourtCode())
                .envId(entity.getSaleEnvironment().getEnvId())
                .courtName(entity.getCourtName())
                .active(entity.isActive())
                .schedules(schedules)
                .build();
    }

    public BadmintonBookingModel toBookingModel(BadmintonBooking entity) {
        return BadmintonBookingModel.builder()
                .bookingCode(entity.getBookingCode())
                .courtCode(entity.getCourt().getCourtCode())
                .courtName(entity.getCourt().getCourtName())
                .date(entity.getBookingDate().toString())
                .startTime(format(entity.getStartTime()))
                .endTime(format(entity.getEndTime()))
                .priceVnd(entity.getPriceVnd())
                .status(entity.getStatus())
                .buyerUsername(entity.getBuyerUsername())
                .buyerName(entity.getBuyerName())
                .note(entity.getNote())
                .build();
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String trimmed = value.trim();
            return LocalTime.parse(trimmed.length() == 5 ? trimmed : trimmed.substring(0, 5));
        } catch (Exception e) {
            return null;
        }
    }

    private static String format(LocalTime time) {
        return time == null ? null : time.format(HHMM);
    }

    private static String cleanBuyerName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > 100) {
            throw new ApplicationException("Buyer name may not exceed 100 characters.", 400);
        }
        return value.trim();
    }

    private static String cleanNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > 255) {
            throw new ApplicationException("Note may not exceed 255 characters.", 400);
        }
        return value.trim();
    }
}
