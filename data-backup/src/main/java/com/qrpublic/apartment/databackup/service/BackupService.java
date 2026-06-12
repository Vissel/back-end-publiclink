package com.qrpublic.apartment.databackup.service;
import com.qrpublic.apartment.databackup.entity.BackupFileEntity;
import com.qrpublic.apartment.databackup.entity.TriggerEntity;
import com.qrpublic.apartment.databackup.entity.TriggerEntity.*;
import com.qrpublic.apartment.databackup.model.BackupResult;
import com.qrpublic.apartment.databackup.model.BackupResult.SchemaBackupResult;
import com.qrpublic.apartment.databackup.repository.BackupFileRepository;
import com.qrpublic.apartment.databackup.repository.TriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class BackupService {
    private final BackupExecutor backupExecutor;
    private final TriggerRepository triggerRepository;
    private final BackupFileRepository backupFileRepository;
    private final EmailSummaryService emailSummaryService;
    private static final List<String> ALL_SCHEMAS = Arrays.asList("publiclink-db", "user_schema");

    @Async
    public void executeBackup(TriggerType triggerType, ScheduleType scheduleType, BackupScope scope, List<String> schemas, String otpSessionId) {
        LocalDateTime startedAt = LocalDateTime.now();
        TriggerEntity trigger = TriggerEntity.builder().triggerType(triggerType).scheduleType(scheduleType)
            .backupScope(scope).status(TriggerStatus.RUNNING).startedAt(startedAt).otpSessionId(otpSessionId).build();
        trigger = triggerRepository.save(trigger);
        boolean full = scope == BackupScope.FULL;
        List<String> targets = schemas != null && !schemas.isEmpty() ? schemas : ALL_SCHEMAS;
        BackupResult result = BackupResult.builder().triggerId(trigger.getTriggerId()).triggerType(triggerType.name())
            .scheduleType(scheduleType != null ? scheduleType.name() : null).scope(scope.name()).startedAt(startedAt).build();
        try {
            for (String schema : targets) {
                SchemaBackupResult sr = backupExecutor.execSchema(schema, full, triggerType.name(), scope.name());
                result.getSchemaResults().add(sr);
                result.setTotalFileSizeBytes(result.getTotalFileSizeBytes() + sr.getFileSizeBytes());
                if (sr.getFilePath() != null) {
                    backupFileRepository.save(BackupFileEntity.builder().trigger(trigger).schemaName(schema)
                        .filePath(sr.getFilePath()).fileSizeBytes(sr.getFileSizeBytes()).rowCount(sr.getTotalRows()).build());
                }
            }
            LocalDateTime done = LocalDateTime.now();
            result.setCompletedAt(done); result.setStatus("COMPLETED");
            result.setDurationSeconds(java.time.Duration.between(startedAt, done).getSeconds());
            trigger.setStatus(TriggerStatus.COMPLETED); trigger.setCompletedAt(done); triggerRepository.save(trigger);
            log.info("Backup completed: triggerId={}, duration={}s", trigger.getTriggerId(), result.getDurationSeconds());
        } catch (Exception e) {
            LocalDateTime done = LocalDateTime.now();
            result.setCompletedAt(done); result.setStatus("FAILED"); result.setErrorMessage(e.getMessage());
            result.setDurationSeconds(java.time.Duration.between(startedAt, done).getSeconds());
            trigger.setStatus(TriggerStatus.FAILED); trigger.setCompletedAt(done); trigger.setErrorMessage(e.getMessage());
            triggerRepository.save(trigger);
            log.error("Backup failed: triggerId={}", trigger.getTriggerId(), e);
        }
        emailSummaryService.sendSummary(result);
    }
}
