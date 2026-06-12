CREATE TABLE IF NOT EXISTS otp_session_tbl (
    session_id VARCHAR(36) PRIMARY KEY, otp_code VARCHAR(6) NULL, otp_hash VARCHAR(128) NULL,
    status ENUM('ISSUED','VERIFIED','EXPIRED','USED') NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, verified_at TIMESTAMP NULL,
    source_ip VARCHAR(45) NULL, INDEX idx_otp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
