-- Chat attachments (changeset 004).
--
-- A chat message may carry exactly one binary payload: an image pasted or
-- picked in the composer, or a standard document attachment. The bytes live on
-- the chat-app host disk under chat.upload-dir; this row is the metadata the
-- clients need to render and download it, keyed by an unguessable uuid.
--
-- kind         : IMAGE (rendered inline as a thumbnail) or FILE (download row).
-- storage_key  : file name on disk (uuid + extension), never the user's name.
-- file_name    : the sender's original name, sanitised, display only.
-- size_bytes   : enforced against chat.max-image-bytes / chat.max-file-bytes
--                before the row is written.
-- The FK cascades: deleting a message removes its attachment metadata (disk
-- cleanup is best effort at read time).

CREATE TABLE chat_attachment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  uuid        VARCHAR(36)  NOT NULL,
  message_id  BIGINT       NOT NULL,
  kind        VARCHAR(10)  NOT NULL,
  file_name   VARCHAR(255) NOT NULL,
  mime_type   VARCHAR(100) NOT NULL,
  size_bytes  BIGINT       NOT NULL,
  storage_key VARCHAR(64)  NOT NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_chat_attachment_uuid UNIQUE (uuid),
  CONSTRAINT fk_chat_attachment_message FOREIGN KEY (message_id)
    REFERENCES chat_message(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_attachment_message ON chat_attachment(message_id);
