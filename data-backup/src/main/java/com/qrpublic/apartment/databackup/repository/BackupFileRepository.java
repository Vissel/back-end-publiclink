package com.qrpublic.apartment.databackup.repository;

import com.qrpublic.apartment.databackup.entity.BackupFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BackupFileRepository extends JpaRepository<BackupFileEntity, Long> {
    List<BackupFileEntity> findByTrigger_TriggerId(Long triggerId);
}
