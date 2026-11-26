-- Changeset 036: split the plan catalog into two purpose groups.
--
--   PRODUCT  : normal product sale links. Every pre-existing row belongs here,
--              which the NOT NULL DEFAULT handles in place, so no UPDATE is
--              needed and prices seeded by 021/024 stay exactly as they are.
--   BOOKING  : yoga class and badminton court booking links.
--
-- The grouping is enforced when an environment is created: a BOOKING plan buys
-- booking links and nothing else, so an account that bought the wrong kind is
-- told so instead of silently wasting the money.
--
-- Keyed on the full (fee, plan_day, env_count) triple rather than (fee,
-- plan_day): the two groups genuinely overlap on price/duration pairs such as
-- 15000 / 7 days, so a looser key would skip a booking row that must exist.

ALTER TABLE payment_plan
    ADD COLUMN plan_group VARCHAR(20) NOT NULL DEFAULT 'PRODUCT';

-- Booking catalog, the agreed price list. Idempotent: only combos that are not
-- present yet are inserted.
INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 5000, 1, 1, '5,000 VND / 1 day / 1 booking link', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 5000 AND plan_day = 1 AND env_count = 1 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 15000, 7, 1, '15,000 VND / 7 days / 1 booking link', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 15000 AND plan_day = 7 AND env_count = 1 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 20000, 7, 3, '20,000 VND / 7 days / 3 booking links', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 7 AND env_count = 3 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 20000, 14, 1, '20,000 VND / 14 days / 1 booking link', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 20000 AND plan_day = 14 AND env_count = 1 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 25000, 14, 3, '25,000 VND / 14 days / 3 booking links', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 25000 AND plan_day = 14 AND env_count = 3 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 25000, 30, 1, '25,000 VND / 30 days / 1 booking link', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 25000 AND plan_day = 30 AND env_count = 1 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 30000, 30, 3, '30,000 VND / 30 days / 3 booking links', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 30000 AND plan_day = 30 AND env_count = 3 AND plan_group = 'BOOKING');

INSERT INTO payment_plan (plan_code, fee, plan_day, env_count, description, plan_state, plan_group)
SELECT UUID(), 35000, 30, 5, '35,000 VND / 30 days / 5 booking links', 'ACTIVE', 'BOOKING'
WHERE NOT EXISTS (SELECT 1 FROM payment_plan WHERE fee = 35000 AND plan_day = 30 AND env_count = 5 AND plan_group = 'BOOKING');
