-- Self-registration captures the seller's contact email on the local user row.
-- Nullable: existing rows (invite-registered sellers, buyers) keep no email.
ALTER TABLE `user`
    ADD COLUMN email VARCHAR(100) NULL;
