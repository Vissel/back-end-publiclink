package com.qrpublic.apartment.databackup.controller;
import com.qrpublic.apartment.databackup.entity.BackupFileEntity;
import com.qrpublic.apartment.databackup.entity.BackupWatermarkEntity;
import com.qrpublic.apartment.databackup.entity.TriggerEntity;
import com.qrpublic.apartment.databackup.entity.TriggerEntity.*;
import com.qrpublic.apartment.databackup.model.BackupRequest;
import com.qrpublic.apartment.databackup.model.TriggerHistoryDTO;
import com.qrpublic.apartment.databackup.repository.BackupFileRepository;
import com.qrpublic.apartment.databackup.repository.TriggerRepository;
import com.qrpublic.apartment.databackup.repository.WatermarkRepository;
import com.qrpublic.apartment.databackup.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/backup") @RequiredArgsConstructor
public class BackupController {
    private final BackupService backupService;
    private final TriggerRepository triggerRepository;
    private final BackupFileRepository backupFileRepository;
    private final WatermarkRepository watermarkRepository;

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerBackup(@RequestBody BackupRequest request) {
        BackupScope scope;
        try { scope = BackupScope.valueOf(request.getScope().toUpperCase()); } catch (Exception e) { scope = BackupScope.INCREMENTAL; }
        backupService.executeBackup(TriggerType.MANUAL, null, scope, request.getSchemas(), null);
        return ResponseEntity.ok(Map.of("message", "Backup triggered", "scope", scope.name()));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TriggerHistoryDTO>> getHistory(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TriggerEntity> triggers = triggerRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(triggers.map(this::toDTO));
    }

    @GetMapping("/history/{triggerId}")
    public ResponseEntity<?> getTriggerDetail(@PathVariable Long triggerId) {
        return triggerRepository.findById(triggerId).map(t -> ResponseEntity.ok(toDTO(t))).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/files/{triggerId}")
    public ResponseEntity<List<BackupFileEntity>> getFiles(@PathVariable Long triggerId) {
        return ResponseEntity.ok(backupFileRepository.findByTrigger_TriggerId(triggerId));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable Long fileId) {
        return backupFileRepository.findById(fileId).map(file -> {
            try { Files.deleteIfExists(Path.of(file.getFilePath())); backupFileRepository.delete(file);
                return ResponseEntity.ok(Map.of("message", "Deleted: " + file.getFilePath()));
            } catch (IOException e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/watermarks")
    public ResponseEntity<List<BackupWatermarkEntity>> getWatermarks() { return ResponseEntity.ok(watermarkRepository.findAll()); }

    private TriggerHistoryDTO toDTO(TriggerEntity t) {
        List<BackupFileEntity> files = backupFileRepository.findByTrigger_TriggerId(t.getTriggerId());
        long totalSize = files.stream().mapToLong(f -> f.getFileSizeBytes() != null ? f.getFileSizeBytes() : 0).sum();
        return TriggerHistoryDTO.builder().triggerId(t.getTriggerId()).triggerType(t.getTriggerType().name())
            .scheduleType(t.getScheduleType() != null ? t.getScheduleType().name() : null)
            .backupScope(t.getBackupScope().name()).status(t.getStatus().name())
            .startedAt(t.getStartedAt()).completedAt(t.getCompletedAt()).createdAt(t.getCreatedAt())
            .errorMessage(t.getErrorMessage()).fileCount(files.size()).totalSizeBytes(totalSize).build();
    }
}
