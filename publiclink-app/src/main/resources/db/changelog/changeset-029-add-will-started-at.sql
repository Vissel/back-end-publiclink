-- Changeset 029: sale environments get an explicit sale window.
--
-- sale_environment.will_started_at : when the window opens. Nullable: rows
--   created before this column exist started as soon as they were created,
--   and a null still means exactly that. The creation flow stores the resolved
--   start (never in the past); the order path refuses orders before it.
-- The closing side already exists as will_ended_at; sellers may now move it
-- earlier (close early) but never past the paid plan end, which the service
-- enforces because the column alone cannot.

ALTER TABLE sale_environment
    ADD COLUMN will_started_at TIMESTAMP NULL;
