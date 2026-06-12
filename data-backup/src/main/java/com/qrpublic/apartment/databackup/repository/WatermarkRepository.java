package com.qrpublic.apartment.databackup.repository;

import com.qrpublic.apartment.databackup.entity.BackupWatermarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WatermarkRepository extends JpaRepository<BackupWatermarkEntity, Long> {
    Optional<BackupWatermarkEntity> findBySchemaNameAndTableName(String schemaName, String tableName);
}
