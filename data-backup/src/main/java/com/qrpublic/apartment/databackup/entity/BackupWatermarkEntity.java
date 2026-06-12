package com.qrpublic.apartment.databackup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "backup_watermark_tbl")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupWatermarkEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watermark_id")
    private Long watermarkId;

    @Column(name = "schema_name", nullable = false, length = 50)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "last_backup_at", nullable = false)
    private LocalDateTime lastBackupAt;

    @Column(name = "last_max_id")
    private Long lastMaxId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
