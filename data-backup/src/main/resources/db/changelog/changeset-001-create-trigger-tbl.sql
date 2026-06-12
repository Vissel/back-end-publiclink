CREATE TABLE IF NOT EXISTS trigger_tbl (
    trigger_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_type ENUM('SCHEDULED','MANUAL') NOT NULL,
    schedule_type ENUM('DAILY','WEEKLY','MONTHLY') NULL,
    backup_scope ENUM('FULL','INCREMENTAL') NOT NULL,
    status ENUM('PENDING','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP NULL, completed_at TIMESTAMP NULL,
    otp_session_id VARCHAR(36) NULL, error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trigger_status (status), INDEX idx_trigger_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
