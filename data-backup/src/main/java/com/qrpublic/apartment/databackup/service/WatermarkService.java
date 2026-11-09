package com.qrpublic.apartment.databackup.service;
import com.qrpublic.apartment.databackup.entity.BackupWatermarkEntity;
import com.qrpublic.apartment.databackup.repository.WatermarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @Slf4j
public class WatermarkService {
    private final WatermarkRepository watermarkRepository;

    @Transactional(readOnly = true)
    public BackupWatermarkEntity getWatermark(String schema, String table) {
        return watermarkRepository.findBySchemaNameAndTableName(schema, table).orElse(null);
    }
    @Transactional
    public void updateWatermark(String schema, String table, LocalDateTime backupTime, Long maxId) {
        BackupWatermarkEntity w = watermarkRepository.findBySchemaNameAndTableName(schema, table)
            .orElse(BackupWatermarkEntity.builder().schemaName(schema).tableName(table).build());
        w.setLastBackupAt(backupTime); w.setLastMaxId(maxId);
        watermarkRepository.save(w);
    }
    @Transactional(readOnly = true)
    public LocalDateTime getLastBackupAt(String s, String t) { BackupWatermarkEntity w = getWatermark(s,t); return w != null ? w.getLastBackupAt() : null; }
    @Transactional(readOnly = true)
    public Long getLastMaxId(String s, String t) { BackupWatermarkEntity w = getWatermark(s,t); return w != null ? w.getLastMaxId() : null; }
}
