package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.entity.YogaClass;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.repository.YogaClassRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Core layer for the weekly yoga class templates. The only layer touching
 * YogaClass entities; everything above works with YogaClassModel and the
 * classCode identifier.
 */
@Slf4j
@Service
public class CoreYogaClassService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private YogaClassRepository yogaClassRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Transactional
    public YogaClassModel createClass(String envId, YogaClassModel draft) {
        SaleEnvironment env = saleEnvironmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));
        assertTimes(draft);
        assertNoOverlap(envId, draft.getDayOfWeek(), parse(draft.getStartTime()), parse(draft.getEndTime()), null);

        YogaClass entity = new YogaClass();
        entity.setClassCode(UUID.randomUUID().toString());
        entity.setSaleEnvironment(env);
        applyDraft(entity, draft);
        return toModel(yogaClassRepository.save(entity));
    }

    @Transactional
    public YogaClassModel updateClass(String classCode, YogaClassModel draft) {
        YogaClass entity = getEntity(classCode);
        assertTimes(draft);
        String envId = entity.getSaleEnvironment().getEnvId();
        assertNoOverlap(envId, draft.getDayOfWeek(), parse(draft.getStartTime()), parse(draft.getEndTime()), classCode);

        applyDraft(entity, draft);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return toModel(yogaClassRepository.save(entity));
    }

    /**
     * Soft delete: the row stays for booking history, the calendar hides it.
     */
    @Transactional
    public void deactivateClass(String classCode) {
        YogaClass entity = getEntity(classCode);
        entity.setActive(false);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        yogaClassRepository.save(entity);
        log.info("Deactivated yoga class [{}]", classCode);
    }

    @Transactional(readOnly = true)
    public List<YogaClassModel> findActiveByEnv(String envId) {
        return yogaClassRepository.findActiveByEnvId(envId).stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public YogaClassModel findByCode(String classCode) {
        return toModel(getEntity(classCode));
    }

    private YogaClass getEntity(String classCode) {
        return yogaClassRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException("Yoga class not found: " + classCode));
    }

    private void applyDraft(YogaClass entity, YogaClassModel draft) {
        entity.setDayOfWeek(draft.getDayOfWeek());
        entity.setStartTime(parse(draft.getStartTime()));
        entity.setEndTime(parse(draft.getEndTime()));
        entity.setTitle(draft.getTitle());
        entity.setCoach(draft.getCoach());
        entity.setCapacity(draft.getCapacity());
        if (draft.getActive() != null) {
            entity.setActive(draft.getActive());
        }
    }

    private void assertTimes(YogaClassModel draft) {
        LocalTime start = parse(draft.getStartTime());
        LocalTime end = parse(draft.getEndTime());
        if (start == null || end == null) {
            throw new ApplicationException("start/end time are required in HH:mm 24h format.", 400);
        }
        if (!end.isAfter(start)) {
            throw new ApplicationException("Class end time must be after start time.", 400);
        }
        if (draft.getCapacity() <= 0) {
            throw new ApplicationException("Capacity must be greater than zero.", 400);
        }
        if (draft.getDayOfWeek() < 1 || draft.getDayOfWeek() > 7) {
            throw new ApplicationException("dayOfWeek must be between 1 (Monday) and 7 (Sunday).", 400);
        }
    }

    private void assertNoOverlap(String envId, int dayOfWeek, LocalTime start, LocalTime end, String excludeCode) {
        List<YogaClass> overlaps = yogaClassRepository.findOverlapping(envId, dayOfWeek, start, end, excludeCode);
        if (!overlaps.isEmpty()) {
            throw new ApplicationException(
                    "Time frame overlaps class '" + overlaps.get(0).getTitle() + "' on the same weekday.", 400);
        }
    }

    public static LocalTime parse(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim().length() == 5 ? hhmm.trim() : hhmm.trim().substring(0, 5));
        } catch (Exception e) {
            return null;
        }
    }

    public static String format(LocalTime time) {
        return time == null ? null : time.format(HHMM);
    }

    public YogaClassModel toModel(YogaClass entity) {
        return YogaClassModel.builder()
                .classCode(entity.getClassCode())
                .envId(entity.getSaleEnvironment().getEnvId())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(format(entity.getStartTime()))
                .endTime(format(entity.getEndTime()))
                .title(entity.getTitle())
                .coach(entity.getCoach())
                .capacity(entity.getCapacity())
                .active(entity.isActive())
                .build();
    }
}
