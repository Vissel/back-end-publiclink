-- Changeset 040: the seller chooses when the plan they buy should start.
--
--   requested_start_at : the start the seller asked for when they picked the
--                        plan, kept separate from active_date because the plan
--                        does not run yet: the order still has to be paid and
--                        the money verified. At activation the requested start
--                        is honoured when it is still in the future and the
--                        admin did not pick a different date deliberately;
--                        otherwise the admin's date wins, so a plan is never
--                        backdated past the money arriving.
--
--   Nullable: admin-assigned rows, extension orders, conversion orders and
--   every row written before this changeset have no request of their own and
--   keep starting on the admin's date exactly as before.

ALTER TABLE payment_plan_user
    ADD COLUMN requested_start_at DATETIME NULL;