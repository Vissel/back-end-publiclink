-- Zalo Chat Bridge: client app chat <-> admin Zalo
-- Tables: chat_device, chat_conversation, chat_message, zalo_credential, zalo_event_log

CREATE TABLE chat_device (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id    VARCHAR(64)  NOT NULL,
  fingerprint  VARCHAR(128) NULL,
  ip_hash      VARCHAR(64)  NULL,
  user_agent   VARCHAR(300) NULL,
  visit_count  INT          NOT NULL DEFAULT 1,
  first_seen   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen    TIMESTAMP    NULL,
  CONSTRAINT uq_chat_device_device_id UNIQUE (device_id)
);

CREATE INDEX idx_chat_device_fingerprint ON chat_device(fingerprint);
CREATE INDEX idx_chat_device_ip_hash ON chat_device(ip_hash);

CREATE TABLE chat_conversation (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  uuid              VARCHAR(64)  NOT NULL,
  device_id         VARCHAR(64)  NOT NULL,
  status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  client_tag        VARCHAR(20)  NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NULL,
  last_message_at   TIMESTAMP    NULL,
  CONSTRAINT uq_chat_conversation_uuid UNIQUE (uuid)
);

CREATE INDEX idx_chat_conversation_device ON chat_conversation(device_id, id);
CREATE INDEX idx_chat_conversation_status ON chat_conversation(status, last_message_at);

CREATE TABLE chat_message (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id   BIGINT       NOT NULL,
  client_message_id VARCHAR(64)  NULL,
  direction         VARCHAR(10)  NOT NULL,
  status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  content           TEXT         NOT NULL,
  zalo_message_id   VARCHAR(64)  NULL,
  quote_message_id  BIGINT       NULL,
  error_code        INT          NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NULL,
  delivered_at      TIMESTAMP    NULL,
  seen_at           TIMESTAMP    NULL,
  CONSTRAINT uq_chat_message_client_id UNIQUE (client_message_id),
  CONSTRAINT uq_chat_message_zalo_id UNIQUE (zalo_message_id),
  CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id)
);

CREATE INDEX idx_chat_message_conversation ON chat_message(conversation_id, id);
CREATE INDEX idx_chat_message_direction ON chat_message(conversation_id, direction);

-- Generic key/value store for Zalo credentials (OA token, admin user id binding)
CREATE TABLE zalo_credential (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  scope_key     VARCHAR(64)  NOT NULL,
  value_a       TEXT         NULL,
  value_b       TEXT         NULL,
  expires_at    BIGINT       NULL,
  updated_at    TIMESTAMP    NULL,
  CONSTRAINT uq_zalo_credential_scope UNIQUE (scope_key)
);

-- Raw webhook events: audit trail + idempotency guard
CREATE TABLE zalo_event_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id    VARCHAR(64)  NOT NULL,
  event_name  VARCHAR(64)  NULL,
  payload     TEXT         NULL,
  processed   TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_zalo_event_log_event_id UNIQUE (event_id)
);

CREATE INDEX idx_zalo_event_log_processed ON zalo_event_log(processed, id);
