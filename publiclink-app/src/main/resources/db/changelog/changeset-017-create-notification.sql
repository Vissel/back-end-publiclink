CREATE TABLE notification (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  title         VARCHAR(200) NOT NULL,
  message       TEXT         NOT NULL,
  target_type   VARCHAR(20)  NOT NULL DEFAULT 'SELLER',
  created_by    VARCHAR(100) NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NULL
);

CREATE TABLE notification_recipient (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  notification_id BIGINT       NOT NULL,
  seller_username VARCHAR(100) NOT NULL,
  request_uuid    VARCHAR(100) NULL,
  is_read         TINYINT(1)   NOT NULL DEFAULT 0,
  read_at         TIMESTAMP    NULL,
  CONSTRAINT fk_notification FOREIGN KEY (notification_id) REFERENCES notification(id)
);

CREATE INDEX idx_notif_recipient_seller ON notification_recipient(seller_username, is_read);
CREATE INDEX idx_notif_recipient_notif  ON notification_recipient(notification_id);
