-- Changeset 027: seller payment plan for yoga buyers + password setup tokens
-- seller_payment_plan is defined by the seller (buyer name, days mapped to a
-- VND amount) and linked to the sale environment it belongs to. A PLAN booking
-- consumes one day (used_days). password_setup_token gates the buyer/seller
-- password setup and admin-triggered reset links (single use, dated).
-- NOTE: seller_plan_id in yoga_booking (changeset 026) intentionally carries
-- no FK so the tables can be created in file order.

CREATE TABLE IF NOT EXISTS seller_payment_plan (
    seller_plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_plan_code VARCHAR(36) NOT NULL UNIQUE,
    env_id VARCHAR(50) NOT NULL,
    seller_username VARCHAR(50) NOT NULL,
    buyer_name VARCHAR(100) NOT NULL,
    buyer_username VARCHAR(50) NOT NULL,
    buyer_email VARCHAR(150),
    total_days INT NOT NULL,
    used_days INT NOT NULL DEFAULT 0,
    amount_vnd DECIMAL(15,2) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_spp_env FOREIGN KEY (env_id) REFERENCES sale_environment(env_id),
    CONSTRAINT uq_spp_buyer_env UNIQUE (env_id, buyer_username),
    KEY idx_spp_seller (seller_username)
);

CREATE TABLE IF NOT EXISTS password_setup_token (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_code VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    requested_by VARCHAR(50),
    expires_at TIMESTAMP NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
