-- Changeset 031: a sale environment may show one transfer account on its page.
--
-- sale_environment.transfer_code : transfer_info.transfer_code of the account
--   displayed (QR, number, name) in the sale space header so buyers can pay.
--   Nullable: null means the seller chose not to show one. Stored as the code,
--   not a foreign key, so removing (deactivating) the account never blocks on
--   the environment; the page simply stops showing a non-ACTIVE account.
--   The owner is either the seller of the environment or an administrator.

ALTER TABLE sale_environment
    ADD COLUMN transfer_code VARCHAR(36) NULL;
