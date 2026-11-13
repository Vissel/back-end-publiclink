package com.qrpublic.apartment.domain.yoga;

import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.model.YogaOccurrenceModel;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.qrpublic.apartment.core.service.CoreSellerPaymentPlanService;
import com.qrpublic.apartment.core.service.CoreYogaBookingService;
import com.qrpublic.apartment.core.service.CoreYogaClassService;
import com.qrpublic.apartment.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Booking business rules: calendar assembly, slot reservation with the
 * buyer-identity decision tree, cancellation with plan-day refund, and the
 * date-range occurrence list shown below the calendar.
 */
@Slf4j
@Component
public class YogaBookingDomain {

    public static final String MODE_PLAN = CoreYogaBookingService.MODE_PLAN;
    public static final String MODE_PAYG = CoreYogaBookingService.MODE_PAY_AS_YOU_GO;

    @Autowired
    private YogaEnvAccess yogaEnvAccess;

    @Autowired
    private CoreYogaClassService coreYogaClassService;

    @Autowired
    private CoreYogaBookingService coreYogaBookingService;

    @Autowired
    private CoreSellerPaymentPlanService coreSellerPaymentPlanService;

    @Autowired
    private CoreNotificationService coreNotificationService;

    @Autowired
    private com.qrpublic.apartment.core.service.CoreUserService coreUserService;

    /**
     * Weekly grid for the calendar screen. weekStart must be a Monday.
     * callerUsername is the gateway-forwarded identity (may be blank); the
     * seller-vs-buyer view is derived from env ownership here.
     */
    @Transactional(readOnly = true)
    public List<YogaOccurrenceModel> calendar(String token, LocalDate weekStart, String callerUsername) {
        YogaEnvModel env = yogaEnvAccess.resolveForRead(token);
        boolean callerIsSeller = env.getSellerUsername() != null && env.getSellerUsername().equals(callerUsername);
        return assembleOccurrences(env, weekStart, weekStart.plusDays(6), callerUsername, callerIsSeller);
    }

    /**
     * Below-calendar class list for a date range, classes with 0 remaining
     * included (requirement: show sold-out occurrences too).
     */
    @Transactional(readOnly = true)
    public List<YogaOccurrenceModel> listOccurrences(String token, LocalDate from, LocalDate to,
            String callerUsername) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ApplicationException("A valid date-from/date-to range is required.", 400);
        }
        if (from.plusDays(92).isBefore(to)) {
            throw new ApplicationException("The range may not exceed 92 days.", 400);
        }
        YogaEnvModel env = yogaEnvAccess.resolveForRead(token);
        boolean callerIsSeller = env.getSellerUsername() != null && env.getSellerUsername().equals(callerUsername);
        return assembleOccurrences(env, from, to, callerUsername, callerIsSeller);
    }

    private List<YogaOccurrenceModel> assembleOccurrences(YogaEnvModel env, LocalDate from, LocalDate to,
            String callerUsername, boolean callerIsSeller) {
        List<YogaClassModel> classes = coreYogaClassService.findActiveByEnv(env.getEnvId());
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> bookedCounts = coreYogaBookingService.countBookedByEnvRange(env.getEnvId(), from, to);
        Map<String, YogaBookingModel> myBookings = callerIsSeller || callerUsername == null
                ? Map.of()
                : coreYogaBookingService.findOpenByBuyerInRange(env.getEnvId(), callerUsername, from, to);

        LocalDate today = yogaEnvAccess.dbToday();
        LocalTime nowTime = yogaEnvAccess.dbNowTime();

        List<YogaOccurrenceModel> occurrences = new ArrayList<>();
        for (YogaClassModel yogaClass : classes) {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek().getValue() != yogaClass.getDayOfWeek()) {
                    continue;
                }
                String key = yogaClass.getClassCode() + "|" + date;
                int booked = bookedCounts.getOrDefault(key, 0);
                int remaining = Math.max(0, yogaClass.getCapacity() - booked);
                boolean past = date.isBefore(today)
                        || (date.isEqual(today) && !LocalTime.parse(yogaClass.getStartTime()).isAfter(nowTime));
                YogaBookingModel mine = myBookings.get(key);
                occurrences.add(YogaOccurrenceModel.builder()
                        .classCode(yogaClass.getClassCode())
                        .date(date.toString())
                        .dayOfWeek(yogaClass.getDayOfWeek())
                        .startTime(yogaClass.getStartTime())
                        .endTime(yogaClass.getEndTime())
                        .title(yogaClass.getTitle())
                        .coach(yogaClass.getCoach())
                        .capacity(yogaClass.getCapacity())
                        .booked(booked)
                        .remaining(remaining)
                        .past(past)
                        .myBookingCode(mine != null ? mine.getBookingCode() : null)
                        .myPaymentMode(mine != null ? mine.getPaymentMode() : null)
                        .build());
            }
        }
        return occurrences;
    }

    /**
     * Reserve one slot. The identity decision tree:
     * - seller caller: books on behalf of a plan buyer (PLAN, no buyer
     * password needed) or a walk-in name (PAY_AS_YOU_GO);
     * - buyer caller with an ACTIVE plan in this env: PLAN, requires the
     * password-set account (authenticationMethod BASIC) and one day per
     * date;
     * - anyone else: PAY_AS_YOU_GO with a required buyer name.
     */
    @Transactional
    public YogaBookingModel book(String token, String classCode, LocalDate date, String buyerName,
            String planBuyerUsername, String requestedMode, String callerUsername, boolean callerIsSeller) {
        YogaEnvModel env = yogaEnvAccess.resolveForBooking(token);
        if (date == null) {
            throw new ApplicationException("A booking date is required.", 400);
        }
        if (date.isBefore(yogaEnvAccess.dbToday())) {
            throw new ApplicationException("Cannot book a class in the past.", 400);
        }

        String mode = requestedMode == null || requestedMode.isBlank() ? null : requestedMode.toUpperCase();
        String buyerUsername = null;
        String resolvedName = buyerName;
        Long sellerPlanId = null;

        if (callerIsSeller) {
            if (planBuyerUsername != null && !planBuyerUsername.isBlank()) {
                SellerPlanModel plan = requireActivePlan(env.getEnvId(), planBuyerUsername);
                resolvedName = plan.getBuyerName();
                buyerUsername = plan.getBuyerUsername();
                mode = MODE_PLAN;
            } else {
                assertName(buyerName);
                mode = MODE_PAYG;
            }
        } else {
            SellerPlanModel plan = callerUsername == null || callerUsername.isBlank()
                    ? null
                    : coreSellerPaymentPlanService.findByEnvAndBuyer(env.getEnvId(), callerUsername);
            if (plan != null && !MODE_PAYG.equals(mode)) {
                // plan buyer booking themselves: password must be completed
                if (!isPasswordSet(callerUsername)) {
                    throw new ApplicationException(
                            "Please set your password with the link the seller sent before booking with your plan.",
                            403);
                }
                if (coreYogaBookingService.countPlanBookingsOnDate(callerUsername, date) > 0) {
                    throw new ApplicationException(
                            "Your plan day for this date is already used. One class per day.", 409);
                }
                buyerUsername = callerUsername;
                resolvedName = plan.getBuyerName();
                mode = MODE_PLAN;
            } else {
                // anonymous walk-in, or a plan buyer explicitly paying per visit
                if (buyerName == null || buyerName.isBlank()) {
                    if (callerUsername != null && !callerUsername.isBlank() && plan == null) {
                        buyerUsername = callerUsername;
                        resolvedName = callerUsername;
                    } else {
                        assertName(buyerName);
                    }
                }
                mode = MODE_PAYG;
            }
        }

        if (MODE_PLAN.equals(mode)) {
            sellerPlanId = coreSellerPaymentPlanService.consumeDay(
                    requireActivePlan(env.getEnvId(), buyerUsername).getSellerPlanCode());
        }

        YogaBookingModel booking = coreYogaBookingService.book(classCode, date, buyerUsername,
                resolvedName == null ? null : resolvedName.trim(), sellerPlanId, mode,
                callerIsSeller ? env.getSellerUsername() : buyerUsername, null);

        notifySeller(env, booking);
        return booking;
    }

    @Transactional
    public YogaBookingModel cancel(String token, String bookingCode, String callerUsername,
            boolean callerIsSeller) {
        YogaEnvModel env = yogaEnvAccess.resolveForBooking(token);
        YogaBookingModel booking = coreYogaBookingService.findByCode(bookingCode);

        boolean ownBooking = callerUsername != null && callerUsername.equals(booking.getBuyerUsername());
        if (!callerIsSeller && !ownBooking) {
            throw new ApplicationException("This booking belongs to another buyer.", 403);
        }
        LocalDate date = LocalDate.parse(booking.getDate());
        if (date.isBefore(yogaEnvAccess.dbToday())) {
            throw new ApplicationException("Cannot cancel a class that already happened.", 400);
        }

        YogaBookingModel cancelled = coreYogaBookingService.cancel(bookingCode, callerUsername);
        if (MODE_PLAN.equals(cancelled.getPaymentMode())) {
            coreSellerPaymentPlanService.refundDay(cancelled.getSellerPlanId());
        }
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<YogaBookingModel> sellerBookings(String token, String sellerUsername,
            LocalDate from, LocalDate to) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        if (from == null || to == null) {
            LocalDate today = yogaEnvAccess.dbToday();
            from = today.minusDays(31);
            to = today.plusDays(31);
        }
        return coreYogaBookingService.findByEnvAndRange(env.getEnvId(), from, to);
    }

    @Transactional
    public YogaBookingModel toggleBooking(String token, String sellerUsername, String bookingCode,
            boolean paid, boolean attended) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        YogaBookingModel booking = coreYogaBookingService.findByCode(bookingCode);
        // ownership is class -> env; verify through the class model
        YogaClassModel yogaClass = coreYogaClassService.findByCode(booking.getClassCode());
        if (!env.getEnvId().equals(yogaClass.getEnvId())) {
            throw new ApplicationException("Booking does not belong to this page.", 403);
        }
        return coreYogaBookingService.toggle(bookingCode, paid, attended);
    }

    /** Buyer-facing: open bookings of the caller inside this environment. */
    @Transactional(readOnly = true)
    public List<YogaBookingModel> myBookings(String token, String callerUsername) {
        YogaEnvModel env = yogaEnvAccess.resolveForRead(token);
        if (callerUsername == null || callerUsername.isBlank()) {
            return List.of();
        }
        LocalDate today = yogaEnvAccess.dbToday();
        return coreYogaBookingService.findByEnvAndRange(env.getEnvId(), today.minusDays(7), today.plusDays(92))
                .stream()
                .filter(b -> callerUsername.equals(b.getBuyerUsername()))
                .toList();
    }

    private SellerPlanModel requireActivePlan(String envId, String buyerUsername) {
        SellerPlanModel plan = coreSellerPaymentPlanService.findByEnvAndBuyer(envId, buyerUsername);
        if (plan == null) {
            throw new ApplicationException("This buyer has no payment plan on this page.", 404);
        }
        if (!"ACTIVE".equals(plan.getState())) {
            throw new ApplicationException("This buyer's payment plan is not active.", 403);
        }
        if (plan.getRemainingDays() <= 0) {
            throw new ApplicationException("The buyer's plan has no day left ("
                    + plan.getUsedDays() + "/" + plan.getTotalDays() + "). Ask the seller to top it up.", 403);
        }
        return plan;
    }

    private void assertName(String buyerName) {
        if (buyerName == null || buyerName.isBlank()) {
            throw new ApplicationException("Buyer name is required for a pay-as-you-go booking.", 400);
        }
    }

    private boolean isPasswordSet(String username) {
        com.qrpublic.apartment.core.model.UserModel user = coreUserService.findByUsername(username);
        return user != null
                && user.getAuthenticationEnum() == com.qrpublic.apartment.core.model.AuthenticationEnum.BASIC;
    }

    private void notifySeller(YogaEnvModel env, YogaBookingModel booking) {
        try {
            coreNotificationService.notifyUsernames(
                    List.of(env.getSellerUsername()),
                    "New yoga booking",
                    booking.getTitle() + " on " + booking.getDate() + " " + booking.getStartTime()
                            + " — "
                            + (booking.getBuyerName() != null ? booking.getBuyerName() : booking.getBuyerUsername())
                            + " (" + booking.getPaymentMode() + ")",
                    "YOGA_BOOKING",
                    booking.getBuyerUsername() != null ? booking.getBuyerUsername() : env.getSellerUsername());
        } catch (Exception e) {
            log.warn("Seller notification failed for booking [{}]: {}", booking.getBookingCode(), e.getMessage());
        }
    }
}
