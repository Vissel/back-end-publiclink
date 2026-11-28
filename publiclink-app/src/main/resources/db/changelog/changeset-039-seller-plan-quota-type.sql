-- Changeset 039: a seller package counts either days or bookings.
--
--   used_days was incremented once per PLAN booking and a second booking on the
--   same date was refused with 409, so a 7-day package read as fully used after
--   7 bookings spread over 2 dates and the "Số ngày" column described
--   bookings while claiming to count days.
--
--   quota_type states which unit the quota is measured in, chosen by the seller
--   when the package is created:
--     DAYS     - one day per calendar date, any number of slots inside it;
--     BOOKINGS - one unit per booking.
--   used_bookings keeps the booking count separately, so both numbers stay
--   visible and never get conflated again for either type.
--
--   Existing rows are DAYS packages (that is how they were sold). Their
--   counters are recomputed from the bookings that still stand, which corrects
--   the historical display as well as the meaning.

ALTER TABLE seller_payment_plan
    ADD COLUMN quota_type VARCHAR(10) NOT NULL DEFAULT 'DAYS',
    ADD COLUMN used_bookings INT NOT NULL DEFAULT 0;

UPDATE seller_payment_plan p
SET p.used_bookings = (
        SELECT COUNT(*)
        FROM yoga_booking b
        WHERE b.seller_plan_id = p.seller_plan_id
          AND b.payment_mode = 'PLAN'
          AND b.status = 'BOOKED'),
    p.used_days = (
        SELECT COUNT(DISTINCT b.booking_date)
        FROM yoga_booking b
        WHERE b.seller_plan_id = p.seller_plan_id
          AND b.payment_mode = 'PLAN'
          AND b.status = 'BOOKED');
