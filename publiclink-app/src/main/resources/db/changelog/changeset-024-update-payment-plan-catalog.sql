-- Changeset 024: align the payment plan catalog with the agreed price list
-- (env_count = how many environments may run AT THE SAME TIME).
--
-- The catalog rows are created by changeset 021, which is already applied
-- wherever this runs, so it must not be edited (checksum validation would
-- fail at startup). This changeset updates the seed rows in place instead,
-- keyed on the original (fee, plan_day) pair, and inserts any target
-- combination that is still missing.
--
-- Old -> New
--   3000 /  1 day / 1  ->  3000 /  1 day / 1   (unchanged)
--  10000 /  3 days / 1  -> 10000 /  7 days / 1
--  15000 /  5 days / 1  -> 15000 / 14 days / 1
--  15000 /  7 days / 1  -> 15000 /  7 days / 3
--  20000 /  7 days / 1  -> 20000 / 14 days / 3
--  20000 / 10 days / 1  -> 20000 / 30 days / 1
--  25000 / 10 days / 1  -> 25000 / 30 days / 3
--  30000 / 14 days / 1  -> 30000 / 30 days / 5
--
-- Plans already assigned to a seller keep their plan_id, so existing
-- payment_plan_user rows follow the new values; only rows matching an old seed
-- combination are touched.

-- 3000 / 1 day / 1 environment (already correct, kept for idempotency)
UPDATE payment_plan
SET env_count = 1,
    description = '3,000 VND / 1 day / 1 environment at a time'
WHERE fee = 3000 AND plan_day = 1;

UPDATE payment_plan
SET plan_day = 7,
    env_count = 1,
    description = '10,000 VND / 7 days / 1 environment at a time'
WHERE fee = 10000 AND plan_day = 3;

UPDATE payment_plan
SET plan_day = 14,
    env_count = 1,
    description = '15,000 VND / 14 days / 1 environment at a time'
WHERE fee = 15000 AND plan_day = 5;

UPDATE payment_plan
SET env_count = 3,
    description = '15,000 VND / 7 days / 3 environments at a time'
WHERE fee = 15000 AND plan_day = 7;

UPDATE payment_plan
SET plan_day = 14,
    env_count = 3,
    description = '20,000 VND / 14 days / 3 environments at a time'
WHERE fee = 20000 AND plan_day = 7;

UPDATE payment_plan
SET plan_day = 30,
    env_count = 1,
    description = '20,000 VND / 30 days / 1 environment at a time'
WHERE fee = 20000 AND plan_day = 10;

UPDATE payment_plan
SET plan_day = 30,
    env_count = 3,
    description = '25,000 VND / 30 days / 3 environments at a time'
WHERE fee = 25000 AND plan_day = 10;

UPDATE payment_plan
SET plan_day = 30,
    env_count = 5,
    description = '30,000 VND / 30 days / 5 environments at a time'
WHERE fee = 30000 AND plan_day = 14;

-- Safety net: append any target combination that is still absent (for example
-- a database where 021 seeded different values before this change landed).
INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 3000, 1, 1, '3,000 VND / 1 day / 1 environment at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 3000 AND plan_day = 1);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 10000, 7, 1, '10,000 VND / 7 days / 1 environment at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 10000 AND plan_day = 7);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 15000, 14, 1, '15,000 VND / 14 days / 1 environment at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 15000 AND plan_day = 14);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 15000, 7, 3, '15,000 VND / 7 days / 3 environments at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 15000 AND plan_day = 7);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 20000, 14, 3, '20,000 VND / 14 days / 3 environments at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 14);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 20000, 30, 1, '20,000 VND / 30 days / 1 environment at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 30);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 25000, 30, 3, '25,000 VND / 30 days / 3 environments at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 25000 AND plan_day = 30);

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state)
SELECT UUID(), 30000, 30, 5, '30,000 VND / 30 days / 5 environments at a time', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 30000 AND plan_day = 30);
