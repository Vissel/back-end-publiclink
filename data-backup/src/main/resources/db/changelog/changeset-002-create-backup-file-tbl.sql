CREATE TABLE IF NOT EXISTS backup_file_tbl (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY, trigger_id BIGINT NOT NULL,
    schema_name VARCHAR(50) NOT NULL, file_path VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NULL, row_count INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_backupfile_trigger FOREIGN KEY (trigger_id) REFERENCES trigger_tbl(trigger_id),
    INDEX idx_backupfile_trigger (trigger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
