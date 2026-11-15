-- Changeset 032: a notification can point at the record it talks about, so the
-- bell dialog renders that record (instead of plain message text) and the admin
-- can act on it directly.
--
-- notification.reference_type : what the code below identifies. Currently only
--   PAYMENT, used by "Payment confirmation received": the code is the
--   payment_code, from which the dialog loads amount, plan, contact and status
--   and offers the "Mark paid" action. Nullable: manual admin notifications and
--   older rows stay plain-text.
-- notification.reference_code : external code (payment_code), not a DB id, so
--   the same value can be resolved through the core services.

ALTER TABLE notification
    ADD COLUMN reference_type VARCHAR(30) NULL,
    ADD COLUMN reference_code VARCHAR(64) NULL;
