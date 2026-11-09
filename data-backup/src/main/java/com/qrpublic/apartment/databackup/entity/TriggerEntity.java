package com.qrpublic.apartment.databackup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "trigger_tbl")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trigger_id")
    private Long triggerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type")
    private ScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "backup_scope", nullable = false)
    private BackupScope backupScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TriggerStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "otp_session_id", length = 36)
    private String otpSessionId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TriggerType { SCHEDULED, MANUAL }
    public enum ScheduleType { DAILY, WEEKLY, MONTHLY }
    public enum BackupScope { FULL, INCREMENTAL }
    public enum TriggerStatus { PENDING, RUNNING, COMPLETED, FAILED }
}
