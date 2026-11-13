package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.entity.YogaBooking;
import com.qrpublic.apartment.entity.YogaClass;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.YogaBookingRepository;
import com.qrpublic.apartment.repository.YogaClassRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core layer for yoga bookings. Slot accounting happens here under a
 * pessimistic write lock on the class row so two concurrent bookings on the
 * same occurrence can never oversell capacity.
 */
@Slf4j
@Service
public class CoreYogaBookingService {

    public static final String STATUS_BOOKED = "BOOKED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String MODE_PLAN = "PLAN";
    public static final String MODE_PAY_AS_YOU_GO = "PAY_AS_YOU_GO";

    @Autowired
    private YogaBookingRepository yogaBookingRepository;

    @Autowired
    private YogaClassRepository yogaClassRepository;

    /**
     * Insert a booking after asserting capacity and duplicate rules. The class
     * row is locked first; the caller (domain) has already validated identity,
     * env lifetime and plan days.
     */
    @Transactional
    public YogaBookingModel book(String classCode, LocalDate date, String buyerUsername, String buyerName,
            Long sellerPlanId, String paymentMode, String bookedBy, String note) {
        YogaClass yogaClass = yogaClassRepository.findByClassCodeForUpdate(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Yoga class not found: " + classCode));
        if (!yogaClass.isActive()) {
            throw new ApplicationException("This class is no longer scheduled.", 400);
        }
        if (date.getDayOfWeek().getValue() != yogaClass.getDayOfWeek()) {
            throw new ApplicationException("The selected date does not match the class weekday.", 400);
        }

        long booked = yogaBookingRepository.countBookedSlots(yogaClass.getClassId(), date);
        if (booked >= yogaClass.getCapacity()) {
            throw new ApplicationException("No slot left for this class on the selected date.", 409);
        }

        if (buyerUsername != null && !buyerUsername.isBlank()) {
            yogaBookingRepository.findOpenByBuyerAndSlot(yogaClass.getClassId(), date, buyerUsername)
                    .ifPresent(existing -> {
                        throw new ApplicationException("This buyer already holds a slot in this class on this date.",
                                409);
                    });
        }

        YogaBooking entity = new YogaBooking();
        entity.setBookingCode(UUID.randomUUID().toString());
        entity.setYogaClass(yogaClass);
        entity.setBookingDate(date);
        entity.setBuyerUsername(buyerUsername == null || buyerUsername.isBlank() ? null : buyerUsername);
        entity.setBuyerName(buyerName);
        entity.setSellerPlanId(sellerPlanId);
        entity.setPaymentMode(paymentMode);
        entity.setStatus(STATUS_BOOKED);
        entity.setPaid(MODE_PLAN.equals(paymentMode)); // a consumed plan day IS the payment
        entity.setBookedBy(bookedBy);
        entity.setNote(note);
        YogaBooking saved = yogaBookingRepository.save(entity);
        log.info("Booked class [{}] on [{}] mode [{}] buyer [{}]", classCode, date, paymentMode,
                buyerUsername != null ? buyerUsername : buyerName);
        return toModel(saved);
    }

    @Transactional
    public YogaBookingModel cancel(String bookingCode, String cancelledBy) {
        YogaBooking entity = getEntity(bookingCode);
        if (!STATUS_BOOKED.equals(entity.getStatus())) {
            throw new ApplicationException("Booking is already closed.", 400);
        }
        entity.setStatus(STATUS_CANCELLED);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        log.info("Booking [{}] cancelled by [{}]", bookingCode, cancelledBy);
        return toModel(yogaBookingRepository.save(entity));
    }

    @Transactional
    public YogaBookingModel toggle(String bookingCode, boolean paid, boolean attended) {
        YogaBooking entity = getEntity(bookingCode);
        entity.setPaid(paid);
        entity.setAttended(attended);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return toModel(yogaBookingRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public YogaBookingModel findByCode(String bookingCode) {
        return toModel(getEntity(bookingCode));
    }

    /**
     * Booked slot count per (classCode, date) inside a window, for calendar
     * and list assembly.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> countBookedByEnvRange(String envId, LocalDate from, LocalDate to) {
        Map<String, Integer> result = new HashMap<>();
        for (Object[] row : yogaBookingRepository.countBookedByEnvRange(envId, from, to)) {
            result.put(slotKey((String) row[0], (java.sql.Date) row[1]), ((Number) row[2]).intValue());
        }
        return result;
    }

    /**
     * Open bookings of one account buyer inside an env window, keyed the same
     * way, so the calendar can highlight "my slots".
     */
    @Transactional(readOnly = true)
    public Map<String, YogaBookingModel> findOpenByBuyerInRange(String envId, String buyerUsername,
            LocalDate from, LocalDate to) {
        Map<String, YogaBookingModel> result = new HashMap<>();
        if (buyerUsername == null || buyerUsername.isBlank()) {
            return result;
        }
        for (YogaBooking b : yogaBookingRepository.findOpenByBuyerInRange(envId, buyerUsername, from, to)) {
            result.put(slotKey(b.getYogaClass().getClassCode(),
                    java.sql.Date.valueOf(b.getBookingDate())), toModel(b));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<YogaBookingModel> findByEnvAndRange(String envId, LocalDate from, LocalDate to) {
        return yogaBookingRepository.findByEnvAndDateRange(envId, from, to).stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPlanBookingsOnDate(String buyerUsername, LocalDate date) {
        return yogaBookingRepository.countPlanBookingsOnDate(buyerUsername, date);
    }

    private YogaBooking getEntity(String bookingCode) {
        return yogaBookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingCode));
    }

    public static String slotKey(String classCode, java.sql.Date date) {
        return classCode + "|" + (date == null ? "" : date.toLocalDate().toString());
    }

    public YogaBookingModel toModel(YogaBooking entity) {
        YogaClass yogaClass = entity.getYogaClass();
        return YogaBookingModel.builder()
                .bookingCode(entity.getBookingCode())
                .classCode(yogaClass.getClassCode())
                .title(yogaClass.getTitle())
                .coach(yogaClass.getCoach())
                .date(entity.getBookingDate() == null ? null : entity.getBookingDate().toString())
                .startTime(CoreYogaClassService.format(yogaClass.getStartTime()))
                .endTime(CoreYogaClassService.format(yogaClass.getEndTime()))
                .buyerUsername(entity.getBuyerUsername())
                .buyerName(entity.getBuyerName())
                .sellerPlanId(entity.getSellerPlanId())
                .paymentMode(entity.getPaymentMode())
                .status(entity.getStatus())
                .paid(entity.isPaid())
                .attended(entity.isAttended())
                .bookedBy(entity.getBookedBy())
                .note(entity.getNote())
                .build();
    }
}
