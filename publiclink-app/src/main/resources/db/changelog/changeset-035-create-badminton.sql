-- Changeset 035: badminton courts, weekly schedules and frame bookings.
-- A booking snapshots the schedule price. Any status other than CANCELLED or
-- REJECTED blocks its court/date/time frame.

CREATE TABLE IF NOT EXISTS badminton_court (
    court_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    court_code VARCHAR(36) NOT NULL UNIQUE,
    env_id VARCHAR(50) NOT NULL,
    court_name VARCHAR(100) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_badminton_court_env FOREIGN KEY (env_id) REFERENCES sale_environment(env_id),
    KEY idx_badminton_court_env (env_id, active)
);

CREATE TABLE IF NOT EXISTS badminton_court_schedule (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    court_id BIGINT NOT NULL,
    day_of_week TINYINT NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    slot_minutes INT NOT NULL,
    price_vnd DECIMAL(19,2) NOT NULL,
    CONSTRAINT fk_badminton_schedule_court FOREIGN KEY (court_id) REFERENCES badminton_court(court_id),
    CONSTRAINT uq_badminton_schedule_court_day UNIQUE (court_id, day_of_week)
);

CREATE TABLE IF NOT EXISTS badminton_booking (
    booking_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(36) NOT NULL UNIQUE,
    court_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    buyer_username VARCHAR(50) NOT NULL,
    buyer_name VARCHAR(100),
    price_vnd DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    note VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_badminton_booking_court FOREIGN KEY (court_id) REFERENCES badminton_court(court_id),
    CONSTRAINT fk_badminton_booking_schedule FOREIGN KEY (schedule_id) REFERENCES badminton_court_schedule(schedule_id),
    KEY idx_badminton_booking_lookup (court_id, booking_date, status)
);
