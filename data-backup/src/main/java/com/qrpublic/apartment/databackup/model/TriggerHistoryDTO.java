package com.qrpublic.apartment.databackup.model;
import lombok.*;
import java.time.LocalDateTime;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerHistoryDTO {
    private Long triggerId; private String triggerType; private String scheduleType;
    private String backupScope; private String status; private LocalDateTime startedAt;
    private LocalDateTime completedAt; private LocalDateTime createdAt;
    private String errorMessage; private int fileCount; private long totalSizeBytes;
}
