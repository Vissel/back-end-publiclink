package com.qrpublic.apartment.domain.badminton;

import com.qrpublic.apartment.badminton.response.BadmintonCourtGridResponse;
import com.qrpublic.apartment.badminton.response.BadmintonFrameResponse;
import com.qrpublic.apartment.badminton.response.BadmintonGridResponse;
import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.BadmintonFrameModel;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreBadmintonService;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.integration.ChatMessageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Court-grid assembly, buyer booking flow and seller booking decisions. */
@Slf4j
@Component
public class BadmintonBookingDomain {
    public static final String REFERENCE_TYPE = "COURT_BOOKING";

    @Autowired
    private BadmintonEnvAccess badmintonEnvAccess;

    @Autowired
    private CoreBadmintonService coreBadmintonService;

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private CoreNotificationService coreNotificationService;

    @Autowired
    private ChatMessageClient chatMessageClient;

    @Transactional(readOnly = true)
    public BadmintonGridResponse grid(String token, LocalDate date, String callerUsername) {
        if (date == null) {
            throw new ApplicationException("A grid date is required.", 400);
        }
        YogaEnvModel env = badmintonEnvAccess.resolveForRead(token);
        boolean sellerView = isSeller(env, callerUsername);
        List<BadmintonCourtModel> courts = coreBadmintonService.findCourts(env.getEnvId(), sellerView);
        List<BadmintonBookingModel> blocking = coreBadmintonService.findBlockingByEnvAndDate(env.getEnvId(), date);
        List<BadmintonBookingModel> myBookings = sellerView || callerUsername == null || callerUsername.isBlank()
                ? List.of()
                : coreBadmintonService.findMyBookings(env.getEnvId(), callerUsername).stream()
                        .filter(b -> date.toString().equals(b.getDate()))
                        .toList();

        List<BadmintonCourtGridResponse> courtResponses = new ArrayList<>();
        for (BadmintonCourtModel court : courts) {
            List<BadmintonFrameResponse> frames = new ArrayList<>();
            for (BadmintonFrameModel frame : coreBadmintonService.generateFrames(court, date)) {
                BadmintonBookingModel blocker = blocking.stream()
                        .filter(b -> court.getCourtCode().equals(b.getCourtCode()))
                        .filter(b -> overlaps(b, frame))
                        .findFirst().orElse(null);
                String state = BadmintonFrameModel.FREE;
                if (blocker != null) {
                    state = callerUsername != null && callerUsername.equals(blocker.getBuyerUsername())
                            ? BadmintonFrameModel.MINE : BadmintonFrameModel.BOOKED;
                }
                frames.add(BadmintonFrameResponse.builder()
                        .startTime(frame.getStartTime())
                        .endTime(frame.getEndTime())
                        .priceVnd(frame.getPriceVnd())
                        .state(state)
                        .bookingCode(blocker != null ? blocker.getBookingCode() : null)
                        .build());
            }
            courtResponses.add(BadmintonCourtGridResponse.builder()
                    .courtCode(court.getCourtCode())
                    .courtName(court.getCourtName())
                    .active(Boolean.TRUE.equals(court.getActive()))
                    .frames(frames)
                    .build());
        }
        return BadmintonGridResponse.builder()
                .date(date.toString())
                .courts(courtResponses)
                .sellerView(sellerView)
                .myBookings(myBookings)
                .build();
    }

    @Transactional
    public BadmintonBookingModel book(String token, String courtCode, LocalDate date, String startTime,
            String callerUsername) {
        requireCaller(callerUsername);
        YogaEnvModel env = badmintonEnvAccess.resolveForBooking(token);
        if (date == null) {
            throw new ApplicationException("A booking date is required.", 400);
        }
        if (date.isBefore(badmintonEnvAccess.dbToday())) {
            throw new ApplicationException("Cannot book a court in the past.", 400);
        }
        LocalTime start = parseTime(startTime);
        if (start == null) {
            throw new ApplicationException("startTime must be a valid HH:mm value.", 400);
        }
        if (date.isEqual(badmintonEnvAccess.dbToday()) && !start.isAfter(badmintonEnvAccess.dbNowTime())) {
            throw new ApplicationException("Cannot book a court frame that has already started.", 400);
        }
        BadmintonCourtModel court = coreBadmintonService.findByCode(courtCode);
        if (!env.getEnvId().equals(court.getEnvId())) {
            throw new ApplicationException("Court does not belong to this page.", 403);
        }
        return coreBadmintonService.book(courtCode, date, startTime, callerUsername,
                resolveBuyerName(callerUsername), null);
    }

    @Transactional
    public BadmintonBookingModel paymentConfirm(String token, String bookingCode, String callerUsername) {
        requireCaller(callerUsername);
        YogaEnvModel env = badmintonEnvAccess.resolveForBooking(token);
        BadmintonBookingModel booking = coreBadmintonService.findBookingByCode(bookingCode);
        assertBookingInEnv(booking, env.getEnvId());
        if (!callerUsername.equals(booking.getBuyerUsername())) {
            throw new ApplicationException("This booking belongs to another buyer.", 403);
        }
        BadmintonBookingModel updated = coreBadmintonService.paymentConfirm(bookingCode);
        notifyAfterCommit(List.of(env.getSellerUsername()), "Badminton payment confirmed",
                booking.getBuyerName() + " confirmed transfer for " + booking.getCourtName() + " on "
                        + booking.getDate() + " " + booking.getStartTime() + ".",
                "SELLER", callerUsername, booking.getBookingCode());
        return updated;
    }

    @Transactional
    public BadmintonBookingModel cancel(String token, String bookingCode, String callerUsername) {
        requireCaller(callerUsername);
        YogaEnvModel env = badmintonEnvAccess.resolveForBooking(token);
        BadmintonBookingModel booking = coreBadmintonService.findBookingByCode(bookingCode);
        assertBookingInEnv(booking, env.getEnvId());
        if (!callerUsername.equals(booking.getBuyerUsername()) && !isSeller(env, callerUsername)) {
            throw new ApplicationException("This booking belongs to another buyer.", 403);
        }
        if (LocalDate.parse(booking.getDate()).isBefore(badmintonEnvAccess.dbToday())) {
            throw new ApplicationException("Cannot cancel a court booking that already happened.", 400);
        }
        return coreBadmintonService.cancel(bookingCode);
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> myBookings(String token, String callerUsername) {
        YogaEnvModel env = badmintonEnvAccess.resolveForRead(token);
        if (callerUsername == null || callerUsername.isBlank()) {
            return List.of();
        }
        return coreBadmintonService.findMyBookings(env.getEnvId(), callerUsername);
    }

    @Transactional(readOnly = true)
    public List<BadmintonBookingModel> sellerBookings(String token, String sellerUsername, LocalDate date) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        return date == null
                ? coreBadmintonService.findByEnv(env.getEnvId())
                : coreBadmintonService.findByEnvAndDate(env.getEnvId(), date);
    }

    @Transactional
    public BadmintonBookingModel confirm(String token, String sellerUsername, String bookingCode) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        BadmintonBookingModel booking = coreBadmintonService.findBookingByCode(bookingCode);
        assertBookingInEnv(booking, env.getEnvId());
        BadmintonBookingModel updated = coreBadmintonService.confirm(bookingCode);
        notifyBuyerAndChat(env, updated, "CONFIRMED", null);
        return updated;
    }

    @Transactional
    public BadmintonBookingModel reject(String token, String sellerUsername, String bookingCode, String note) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        BadmintonBookingModel booking = coreBadmintonService.findBookingByCode(bookingCode);
        assertBookingInEnv(booking, env.getEnvId());
        BadmintonBookingModel updated = coreBadmintonService.reject(bookingCode, note);
        notifyBuyerAndChat(env, updated, "REJECTED", updated.getNote());
        return updated;
    }

    private void notifyBuyerAndChat(YogaEnvModel env, BadmintonBookingModel booking, String status, String note) {
        String title = "Badminton booking " + status.toLowerCase();
        String message = "Your booking for " + booking.getCourtName() + " on " + booking.getDate() + " "
                + booking.getStartTime() + " was " + status.toLowerCase() + "."
                + (note == null || note.isBlank() ? "" : " Note: " + note);
        notifyAfterCommit(List.of(booking.getBuyerUsername()), title, message, "BUYER",
                env.getSellerUsername(), booking.getBookingCode());
        dispatchChatAfterCommit(booking.getBuyerUsername(), message);
    }

    private void notifyAfterCommit(List<String> recipients, String title, String message,
            String targetType, String createdBy, String bookingCode) {
        runAfterCommit(() -> {
            try {
                coreNotificationService.notifyUsernames(recipients, title, message, targetType, createdBy,
                        REFERENCE_TYPE, bookingCode);
            } catch (Exception e) {
                log.warn("Badminton notification failed for booking [{}]: {}", bookingCode, e.getMessage());
            }
        });
    }

    private void dispatchChatAfterCommit(String buyerUsername, String content) {
        runAfterCommit(() -> {
            try {
                // Fire-and-forget: both the client and this boundary swallow
                // failures, and afterCommit keeps chat out of the DB transaction.
                chatMessageClient.sendSystemMessage(buyerUsername, content)
                        .onErrorResume(error -> {
                            log.warn("Chat delivery failed for buyer [{}]: {}", buyerUsername, error.getMessage());
                            return Mono.empty();
                        })
                        .subscribe();
            } catch (Exception e) {
                log.warn("Chat dispatch failed for buyer [{}]: {}", buyerUsername, e.getMessage());
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(action);
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } catch (Exception e) {
            log.warn("Could not schedule badminton post-commit action: {}", e.getMessage());
        }
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Badminton post-commit action failed: {}", e.getMessage());
        }
    }

    private void assertBookingInEnv(BadmintonBookingModel booking, String envId) {
        BadmintonCourtModel court = coreBadmintonService.findByCode(booking.getCourtCode());
        if (!envId.equals(court.getEnvId())) {
            throw new ApplicationException("Booking does not belong to this page.", 403);
        }
    }

    private String resolveBuyerName(String callerUsername) {
        try {
            UserModel user = coreUserService.findByUsername(callerUsername);
            if (user != null && user.getName() != null && !user.getName().isBlank()) {
                return user.getName();
            }
        } catch (Exception e) {
            log.warn("Could not resolve buyer name [{}]: {}", callerUsername, e.getMessage());
        }
        return callerUsername;
    }

    private boolean isSeller(YogaEnvModel env, String callerUsername) {
        return callerUsername != null && !callerUsername.isBlank()
                && callerUsername.equals(env.getSellerUsername());
    }

    private boolean overlaps(BadmintonBookingModel booking, BadmintonFrameModel frame) {
        return parseTime(booking.getStartTime()).isBefore(parseTime(frame.getEndTime()))
                && parseTime(booking.getEndTime()).isAfter(parseTime(frame.getStartTime()));
    }

    private void requireCaller(String callerUsername) {
        if (callerUsername == null || callerUsername.isBlank()) {
            throw new ApplicationException("Please sign in", 401);
        }
    }

    private LocalTime parseTime(String value) {
        try {
            String trimmed = value.trim();
            return LocalTime.parse(trimmed.length() == 5 ? trimmed : trimmed.substring(0, 5));
        } catch (Exception e) {
            throw new ApplicationException("Time must be a valid HH:mm value.", 400);
        }
    }
}
