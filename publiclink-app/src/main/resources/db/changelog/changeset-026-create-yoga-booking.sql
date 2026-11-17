-- Changeset 026: yoga booking
-- One row per buyer slot reservation. booking_date is the concrete date of the
-- weekly occurrence (its weekday matches yoga_class.day_of_week). Account
-- buyers carry buyer_username (unique per class+date); anonymous pay-as-you-go
-- buyers leave it NULL and only carry buyer_name, so the unique key does not
-- block them (MySQL/MariaDB unique indexes tolerate multiple NULLs).

CREATE TABLE IF NOT EXISTS yoga_booking (
    booking_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(36) NOT NULL UNIQUE,
    class_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    buyer_username VARCHAR(50),
    buyer_name VARCHAR(100),
    seller_plan_id BIGINT,
    payment_mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    paid TINYINT(1) NOT NULL DEFAULT 0,
    attended TINYINT(1) NOT NULL DEFAULT 0,
    booked_by VARCHAR(50),
    note VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_yoga_booking_class FOREIGN KEY (class_id) REFERENCES yoga_class(class_id),
    CONSTRAINT uq_yoga_booking_buyer UNIQUE (class_id, booking_date, buyer_username),
    KEY idx_yoga_booking_lookup (class_id, booking_date, status)
);
