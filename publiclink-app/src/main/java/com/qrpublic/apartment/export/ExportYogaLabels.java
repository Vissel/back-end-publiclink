package com.qrpublic.apartment.export;

import lombok.Getter;

/**
 * Localized labels for the yoga-specific sections of the seller Excel report:
 * the class schedule with live slot accounting and the registered buyers
 * (yoga bookings live in the yoga_* tables, not in the plain product orders).
 * Mirrors the frontend yoga i18n vocabulary; locale resolution is shared with
 * {@link ExportLabels} through {@link ExportLocale}.
 */
@Getter
public final class ExportYogaLabels {

    private final String sectionClasses;
    private final String sectionBookings;

    private final String classLabel;
    private final String coach;
    private final String weekday;
    private final String time;
    private final String capacity;
    private final String booked;
    private final String remaining;

    private final String date;
    private final String username;
    private final String paymentMode;
    private final String paid;
    private final String attended;
    private final String bookedAt;
    private final String status;

    private final String modePlan;
    private final String modePayAsYouGo;
    private final String statusBooked;
    private final String statusCancelled;

    private final boolean vietnamese;

    private static final String[] WEEKDAYS_EN = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
            "Saturday", "Sunday" };
    private static final String[] WEEKDAYS_VI = { "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7",
            "Chủ nhật" };

    private ExportYogaLabels(
            String sectionClasses,
            String sectionBookings,
            String classLabel,
            String coach,
            String weekday,
            String time,
            String capacity,
            String booked,
            String remaining,
            String date,
            String username,
            String paymentMode,
            String paid,
            String attended,
            String bookedAt,
            String status,
            String modePlan,
            String modePayAsYouGo,
            String statusBooked,
            String statusCancelled,
            boolean vietnamese) {
        this.sectionClasses = sectionClasses;
        this.sectionBookings = sectionBookings;
        this.classLabel = classLabel;
        this.coach = coach;
        this.weekday = weekday;
        this.time = time;
        this.capacity = capacity;
        this.booked = booked;
        this.remaining = remaining;
        this.date = date;
        this.username = username;
        this.paymentMode = paymentMode;
        this.paid = paid;
        this.attended = attended;
        this.bookedAt = bookedAt;
        this.status = status;
        this.modePlan = modePlan;
        this.modePayAsYouGo = modePayAsYouGo;
        this.statusBooked = statusBooked;
        this.statusCancelled = statusCancelled;
        this.vietnamese = vietnamese;
    }

    public static ExportYogaLabels forLocale(String locale) {
        return ExportLocale.VIETNAMESE.equals(ExportLocale.resolve(locale)) ? vietnamese() : english();
    }

    /** ISO weekday (1 = Monday .. 7 = Sunday) as a localized name. */
    public String weekdayName(int isoDayOfWeek) {
        String[] names = vietnamese ? WEEKDAYS_VI : WEEKDAYS_EN;
        if (isoDayOfWeek < 1 || isoDayOfWeek > 7) {
            return String.valueOf(isoDayOfWeek);
        }
        return names[isoDayOfWeek - 1];
    }

    /** Payment mode as shown in the booking list, e.g. PLAN or PAY_AS_YOU_GO. */
    public String paymentModeLabel(String mode) {
        return "PLAN".equals(mode) ? modePlan : modePayAsYouGo;
    }

    /** Booking status as a localized label. */
    public String statusLabel(String status) {
        return "CANCELLED".equals(status) ? statusCancelled : statusBooked;
    }

    private static ExportYogaLabels english() {
        return new ExportYogaLabels(
                "Yoga Classes",
                "Registered Buyers",
                "Class",
                "Coach",
                "Day",
                "Time",
                "Slots",
                "Booked",
                "Remaining",
                "Date",
                "Username",
                "Payment",
                "Paid",
                "Attended",
                "Booked at",
                "Status",
                "Payment plan",
                "Pay per visit",
                "Booked",
                "Cancelled",
                false);
    }

    private static ExportYogaLabels vietnamese() {
        return new ExportYogaLabels(
                "Lớp học",
                "Học viên đã đăng ký",
                "Tên lớp",
                "HLV",
                "Thứ",
                "Giờ",
                "Chỗ",
                "Đã đặt",
                "Còn lại",
                "Ngày",
                "Tên đăng nhập",
                "Thanh toán",
                "Đã trả",
                "Đã tham gia",
                "Đặt lúc",
                "Trạng thái",
                "Dùng gói thanh toán",
                "Trả từng buổi",
                "Đã đặt",
                "Đã hủy",
                true);
    }
}
