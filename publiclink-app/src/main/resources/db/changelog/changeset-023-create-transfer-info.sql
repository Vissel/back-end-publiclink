-- Changeset 023: transfer info (bank / e-wallet accounts)
-- Single table serving both admin and seller owners (owner_type discriminator).
-- Owners are identified by username, not by FK to `user`: administrators are
-- managed by the user service and have no row in publiclink-db.user, so a
-- foreign key here would reject every admin account. DEACTIVE is a soft delete
-- done by the owner. transfer_code is the external identifier.

CREATE TABLE IF NOT EXISTS transfer_info (
    transfer_info_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_code VARCHAR(36) NOT NULL UNIQUE,
    owner_username VARCHAR(50) NOT NULL,
    owner_type VARCHAR(10) NOT NULL,
    transfer_issuer VARCHAR(100) NOT NULL,
    issuer_full_name VARCHAR(150) NULL,
    `type` VARCHAR(20) NOT NULL DEFAULT 'BANK',
    transfer_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NULL,
    qr_image MEDIUMTEXT NULL,
    default_message VARCHAR(250) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_transfer_owner (owner_username, owner_type, status)
);
