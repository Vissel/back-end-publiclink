-- Changeset 038: let two yoga classes share one weekday time frame.
--
--   changeset-025 declared uq_yoga_class_slot UNIQUE (env_id, day_of_week,
--   start_time, end_time), which allowed exactly one class per slot. The
--   schedule rule now permits two concurrent classes on the same frame (two
--   coaches teaching at the same time), so the unique index refused the second
--   one at the database level and the class copy died with
--   DataIntegrityViolationException -> HTTP 500 instead of the service answer.
--
--   The cap itself stays enforced in CoreYogaClassService.assertOverlapLimit,
--   which counts overlapping active classes under the same weekday; this file
--   only replaces the uniqueness with a plain lookup index so the query the
--   cap runs keeps its access path.

ALTER TABLE yoga_class DROP INDEX uq_yoga_class_slot;

CREATE INDEX idx_yoga_class_slot ON yoga_class (env_id, day_of_week, start_time, end_time);
