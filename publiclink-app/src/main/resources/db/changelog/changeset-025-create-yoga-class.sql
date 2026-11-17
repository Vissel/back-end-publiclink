-- Changeset 025: yoga booking product type + weekly class schedule
-- product.product_type discriminates the plain order-taking link (STANDARD)
-- from the calendar-driven yoga link (YOGA_BOOKING). yoga_class is the weekly
-- recurring template owned by one sale environment; bookings target concrete
-- dates matching the class weekday (see changeset 026).

ALTER TABLE product ADD COLUMN product_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD';

CREATE TABLE IF NOT EXISTS yoga_class (
    class_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_code VARCHAR(36) NOT NULL UNIQUE,
    env_id VARCHAR(50) NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    title VARCHAR(200) NOT NULL,
    coach VARCHAR(100),
    capacity INT NOT NULL DEFAULT 1,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_yoga_class_env FOREIGN KEY (env_id) REFERENCES sale_environment(env_id),
    CONSTRAINT uq_yoga_class_slot UNIQUE (env_id, day_of_week, start_time, end_time),
    KEY idx_yoga_class_env_day (env_id, day_of_week)
);
