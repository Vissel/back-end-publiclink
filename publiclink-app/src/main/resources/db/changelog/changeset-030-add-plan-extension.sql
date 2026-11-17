-- Changeset 030: plan extensions become their own payable queue row.
--
-- An "extend plan" action creates a payment_plan_user row that does NOT grant
-- environments; it only buys extra days for an assignment that already exists.
--   extend_days            : how many days were bought. Non-null marks the row
--                            as an extension order, so the plan window rules can
--                            tell it apart from a real subscription.
--   extends_plan_user_code : the plan_user_code of the assignment whose
--                            ended_date grows when this extension is paid.
-- Both nullable: every pre-existing row is a normal plan assignment.

ALTER TABLE payment_plan_user
    ADD COLUMN extend_days INT NULL,
    ADD COLUMN extends_plan_user_code VARCHAR(36) NULL;
