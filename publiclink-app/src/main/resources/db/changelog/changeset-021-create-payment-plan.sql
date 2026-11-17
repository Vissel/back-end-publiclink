-- Changeset 021: payment plan catalog
-- Plans an admin can assign to a seller. plan_code is the external identifier
-- (DB ids are never exposed in API in/out). env_count is the quota unit: number
-- of environments/links the plan allows once ACTIVE.

CREATE TABLE IF NOT EXISTS payment_plan (
    plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(36) NOT NULL UNIQUE,
    fee DECIMAL(15,2) NOT NULL,
    plan_day INT NOT NULL,
    env_count INT NOT NULL DEFAULT 1,
    description VARCHAR(250),
    plan_state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Seed data (idempotent: only inserts combos that are not present yet)
INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 3000, 1, 1, '3,000 VND / 1 day / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 3000 AND plan_day = 1);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 10000, 3, 1, '10,000 VND / 3 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 10000 AND plan_day = 3);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 15000, 5, 1, '15,000 VND / 5 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 15000 AND plan_day = 5);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 15000, 7, 1, '15,000 VND / 7 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 15000 AND plan_day = 7);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 20000, 7, 1, '20,000 VND / 7 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 7);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 20000, 10, 1, '20,000 VND / 10 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 10);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 25000, 10, 1, '25,000 VND / 10 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 25000 AND plan_day = 10);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 30000, 14, 1, '30,000 VND / 14 days / 1 link', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 30000 AND plan_day = 14);
