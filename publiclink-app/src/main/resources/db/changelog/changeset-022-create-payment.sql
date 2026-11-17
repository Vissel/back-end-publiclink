-- Changeset 022: plan assignment, payment and bill tables
-- payment_plan_user maps a user to a plan (a user can have N plans). It carries
-- the environment quota and its own lifecycle: CREATE (awaiting payment/approval)
-- -> ACTIVE (admin approved, quota usable) / DEACTIVE (rejected or deactivated).
-- payment tracks the money side; bill is created when a payment is marked PAID.

CREATE TABLE IF NOT EXISTS payment_plan_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_user_code VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(50) NOT NULL,
    plan_id BIGINT NOT NULL,
    env_quota INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    plan_state VARCHAR(20) NOT NULL DEFAULT 'CREATE',
    active_date DATETIME NULL,
    ended_date DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ppu_user FOREIGN KEY (user_id) REFERENCES `user`(user_id),
    CONSTRAINT fk_ppu_plan FOREIGN KEY (plan_id) REFERENCES payment_plan(plan_id),
    INDEX idx_ppu_user_state (user_id, plan_state)
);

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_code VARCHAR(36) NOT NULL UNIQUE,
    plan_user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATE',
    payment_url VARCHAR(500),
    created_by VARCHAR(50) NULL,
    seller_email VARCHAR(100) NULL,
    seller_phone VARCHAR(20) NULL,
    confirmed_at DATETIME NULL,
    paid_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_plan_user FOREIGN KEY (plan_user_id) REFERENCES payment_plan_user(id),
    INDEX idx_payment_status (status)
);

CREATE TABLE IF NOT EXISTS bill (
    bill_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_code VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    billed_amount DECIMAL(15,2) NOT NULL,
    note VARCHAR(250),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id)
);
