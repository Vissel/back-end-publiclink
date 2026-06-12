package com.qrpublic.apartment.databackup.model;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupResult {
    private Long triggerId; private String triggerType; private String scheduleType;
    private String scope; private String status; private LocalDateTime startedAt;
    private LocalDateTime completedAt; private long durationSeconds; private String errorMessage;
    @Builder.Default private List<SchemaBackupResult> schemaResults = new ArrayList<>();
    private long totalFileSizeBytes; private String backupFilePath;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SchemaBackupResult {
        private String schemaName;
        @Builder.Default private List<TableBackupResult> tableResults = new ArrayList<>();
        private String filePath; private long fileSizeBytes; private int totalRows;
        public void addTableResult(TableBackupResult t) { tableResults.add(t); totalRows += t.getRowCount(); }
    }
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TableBackupResult {
        private String tableName; private int rowCount; private long sizeBytes; private boolean hasChanges;
    }
}
