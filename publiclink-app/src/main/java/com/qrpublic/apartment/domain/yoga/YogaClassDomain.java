package com.qrpublic.apartment.domain.yoga;

import com.qrpublic.apartment.core.model.YogaClassCopyModel;
import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreYogaClassService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.yoga.request.YogaClassCopyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seller-side management of the weekly class schedule. Every entry point
 * asserts the caller owns the environment behind the public link token.
 */
@Slf4j
@Component
public class YogaClassDomain {

    @Autowired
    private YogaEnvAccess yogaEnvAccess;

    @Autowired
    private CoreYogaClassService coreYogaClassService;

    public YogaClassModel createClass(String token, String sellerUsername, YogaClassModel draft) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        return coreYogaClassService.createClass(env.getEnvId(), draft);
    }

    public YogaClassModel updateClass(String token, String sellerUsername, String classCode, YogaClassModel draft) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        assertClassInEnv(classCode, env.getEnvId());
        return coreYogaClassService.updateClass(classCode, draft);
    }

    public void deleteClass(String token, String sellerUsername, String classCode) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        assertClassInEnv(classCode, env.getEnvId());
        coreYogaClassService.deactivateClass(classCode);
    }

    public List<YogaClassModel> listClasses(String token, String sellerUsername) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        return coreYogaClassService.findActiveByEnv(env.getEnvId());
    }

    /**
     * Duplicate one class's schedule info (title, coach, capacity) onto several
     * date + time targets. Each target becomes a new weekly class on that date's
     * weekday; buyer registrations are never copied.
     *
     * <p>
     * Deliberately not {@code @Transactional}: every target is created in its
     * own transaction, so one refused row (past the sale window, malformed, or
     * a third class on the same frame) never discards the ones the seller got
     * right. The refusals come back in the result instead.
     */
    public YogaClassCopyModel copyClass(String token, String sellerUsername, String classCode,
            List<YogaClassCopyRequest.Target> targets) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        yogaEnvAccess.assertAlive(env);
        YogaClassModel source = coreYogaClassService.findByCode(classCode);
        if (!env.getEnvId().equals(source.getEnvId())) {
            throw new ApplicationException("Class does not belong to this page.", 403);
        }

        LocalDate today = yogaEnvAccess.dbToday();
        LocalDate deadline = env.getWillEndedAtMillis() != null
                ? yogaEnvAccess.dbDateOf(env.getWillEndedAtMillis())
                : null;

        List<YogaClassModel> created = new ArrayList<>();
        List<YogaClassCopyModel.FailedTarget> failed = new ArrayList<>();
        for (YogaClassCopyRequest.Target target : targets) {
            LocalDate date = parseDate(target.getDate());
            String reason = refuse(target, date, today, deadline);
            if (reason != null) {
                failed.add(failedTarget(target, reason));
                continue;
            }
            try {
                created.add(coreYogaClassService.createClass(env.getEnvId(), YogaClassModel.builder()
                        .dayOfWeek(date.getDayOfWeek().getValue())
                        .startTime(target.getStartTime().trim())
                        .endTime(target.getEndTime().trim())
                        .title(source.getTitle())
                        .coach(source.getCoach())
                        .capacity(source.getCapacity())
                        .active(Boolean.TRUE)
                        .build()));
            } catch (ApplicationException e) {
                failed.add(failedTarget(target, e.getErrorMessage()));
            }
        }
        log.info("Seller [{}] copied class [{}]: {} created, {} refused", sellerUsername, classCode,
                created.size(), failed.size());
        return YogaClassCopyModel.builder().created(created).failed(failed).build();
    }

    /**
     * Null means the target is acceptable; anything else is the seller-facing
     * reason.
     */
    private String refuse(YogaClassCopyRequest.Target target, LocalDate date, LocalDate today, LocalDate deadline) {
        LocalTime start = CoreYogaClassService.parse(target.getStartTime());
        LocalTime end = CoreYogaClassService.parse(target.getEndTime());
        if (date == null || start == null || end == null) {
            return "A valid date and HH:mm times are required.";
        }
        if (!end.isAfter(start)) {
            return "Class end time must be after start time.";
        }
        if (date.isBefore(today)) {
            return "Cannot schedule a class in the past.";
        }
        if (deadline != null && date.isAfter(deadline)) {
            return "The date is past the end of this sale page.";
        }
        return null;
    }

    private YogaClassCopyModel.FailedTarget failedTarget(YogaClassCopyRequest.Target target, String reason) {
        return YogaClassCopyModel.FailedTarget.builder()
                .date(target.getDate())
                .startTime(target.getStartTime())
                .endTime(target.getEndTime())
                .reason(reason)
                .build();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (Exception e) {
            return null;
        }
    }

    private void assertClassInEnv(String classCode, String envId) {
        YogaClassModel existing = coreYogaClassService.findByCode(classCode);
        if (!envId.equals(existing.getEnvId())) {
            throw new com.qrpublic.apartment.exception.ApplicationException(
                    "Class does not belong to this page.", 403);
        }
    }
}
