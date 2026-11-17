-- Chat console additions (changeset 002): unread counter + browser push subscriptions.
-- Never edit changeset-001; this script only appends.

-- Unread guest messages per conversation, shown as the WhatsApp-style badge in
-- the admin inbox. Reset when the administrator opens the thread.
ALTER TABLE chat_conversation ADD COLUMN unread_count INT NOT NULL DEFAULT 0;

-- One row per browser subscription. The endpoint URL is the natural key: the
-- browser reuses it across logins and re-subscribes with new keys when it
-- rotates them, so an upsert on endpoint keeps the table free of duplicates
-- without needing a client-side subscription id.
CREATE TABLE chat_push_subscription (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_username  VARCHAR(64)   NOT NULL,
  endpoint        VARCHAR(512)  NOT NULL,
  p256dh          VARCHAR(120)  NOT NULL,
  auth            VARCHAR(120)  NOT NULL,
  created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at    TIMESTAMP     NULL,
  CONSTRAINT uq_chat_push_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_chat_push_admin ON chat_push_subscription(admin_username);
