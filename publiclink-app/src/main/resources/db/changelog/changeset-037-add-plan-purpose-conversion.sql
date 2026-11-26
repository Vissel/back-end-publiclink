-- Changeset 037: registration purpose and plan conversion.
--
--   user.plan_purpose              : PRODUCT or BOOKING, chosen when the seller
--                                    registers. It decides which plan group the
--                                    plan screens offer, so nobody browses a
--                                    price list meant for the other kind of
--                                    business. Nullable on purpose: every
--                                    account that exists today, and every
--                                    account an admin creates by hand, keeps
--                                    NULL and is read as PRODUCT.
--
--   converts_plan_user_code        : plan_user_code of the assignment being
--                                    converted. Non-null marks the row as a
--                                    conversion order, exactly the way
--                                    extend_days marks an extension order
--                                    (changeset 030), so the quota and
--                                    expired-plan rules can tell an order apart
--                                    from a real subscription.
--   convert_fee_delta              : the amount actually due, new plan fee
--                                    minus the fee already paid. Stored on the
--                                    row so the queue tables and the payment
--                                    both read the difference instead of the
--                                    full price of the target plan.
--
-- A conversion order grants no environment slot of its own (env_quota 0);
-- paying it replaces the converted plan's catalog entry, window and quota.

ALTER TABLE `user`
    ADD COLUMN plan_purpose VARCHAR(20) NULL;

ALTER TABLE payment_plan_user
    ADD COLUMN converts_plan_user_code VARCHAR(36) NULL,
    ADD COLUMN convert_fee_delta DECIMAL(15,2) NULL;
