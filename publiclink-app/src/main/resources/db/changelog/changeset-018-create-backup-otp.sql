CREATE TABLE IF NOT EXISTS backup_otp (
    session_id VARCHAR(36) PRIMARY KEY, otp_code VARCHAR(6) NOT NULL,
    status ENUM('ISSUED','VERIFIED','EXPIRED','USED') NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, verified_at TIMESTAMP NULL,
    backup_token VARCHAR(255) NULL, token_expires_at TIMESTAMP NULL,
    INDEX idx_backup_otp_status (status), INDEX idx_backup_otp_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
