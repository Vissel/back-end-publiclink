-- Comment schema, changeset 001 (owner: comment-app only).
--
-- Threaded comments on generic targets. A target is (target_type, target_id)
-- with no cross-database FK: PRODUCT uses publiclink-db product ids, POST and
-- GUIDELINE will carry their own ids when those features land. The service
-- never validates target existence, the same loose coupling chat-app uses.
--
-- Tree model:
--   parent_id : the direct parent comment (any depth), NULL for a root.
--   root_id   : the id of the owning root comment (itself for a root), kept
--               denormalised so one indexed query fetches a whole thread.
--   last_activity_at: ONLY meaningful on root rows — max(created_at) of the
--               whole tree. A reply bumps it, which lifts the entire thread,
--               including every sub-reply, to the top of the listing
--               ("newest comment higher; a reply brings the tree higher").
--               Non-root rows just carry their own created_at.
--
-- Author snapshot: author_username comes ONLY from the gateway-forwarded
-- X-User-ID (never the request body); author_display_name is resolved at write
-- time so history keeps the name the comment was posted under.
--
-- Soft delete: deleted=1 keeps the row so children stay reachable; the UI
-- renders a "deleted" placeholder.

CREATE TABLE comment (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  uuid             VARCHAR(36)  NOT NULL,
  target_type      VARCHAR(20)  NOT NULL,
  target_id        VARCHAR(64)  NOT NULL,
  parent_id        BIGINT       NULL,
  root_id          BIGINT       NULL,
  author_username  VARCHAR(100) NOT NULL,
  author_display_name VARCHAR(255) NULL,
  content          TEXT         NOT NULL,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  edited_at        DATETIME(3)  NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_activity_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_comment_uuid UNIQUE (uuid),
  CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id)
    REFERENCES comment(id) ON DELETE CASCADE,
  CONSTRAINT fk_comment_root FOREIGN KEY (root_id)
    REFERENCES comment(id) ON DELETE CASCADE
);

-- The listing query: one target's roots, newest activity first, keyset-paged.
CREATE INDEX idx_comment_target_roots
  ON comment(target_type, target_id, root_id, last_activity_at, id);
-- The thread query: everything under one root, chronological.
CREATE INDEX idx_comment_thread ON comment(root_id, id);
CREATE INDEX idx_comment_parent ON comment(parent_id);

-- One comment may carry several image/file attachments. Bytes live on this
-- host's disk under comment.upload-dir named by a random storage key; this row
-- is the metadata clients render and the download endpoint serves (comments
-- are public, so the unguessable uuid is the capability). Mirrors
-- chat_attachment in chat-app.
CREATE TABLE comment_attachment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  uuid        VARCHAR(36)  NOT NULL,
  comment_id  BIGINT       NOT NULL,
  kind        VARCHAR(10)  NOT NULL,
  file_name   VARCHAR(255) NOT NULL,
  mime_type   VARCHAR(100) NOT NULL,
  size_bytes  BIGINT       NOT NULL,
  storage_key VARCHAR(64)  NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_comment_attachment_uuid UNIQUE (uuid),
  CONSTRAINT fk_comment_attachment_comment FOREIGN KEY (comment_id)
    REFERENCES comment(id) ON DELETE CASCADE
);

CREATE INDEX idx_comment_attachment_comment ON comment_attachment(comment_id);

-- Server-side cache of an author's avatar (and last resolved display name)
-- copied from the user service at write time, so a public comment page can
-- show profile pictures without any new public user-service route. Refreshed
-- at most once per comment.avatar-cache-minutes per username.
CREATE TABLE comment_author_avatar (
  username   VARCHAR(100) PRIMARY KEY,
  data_url   MEDIUMTEXT   NULL,
  full_name  VARCHAR(255) NULL,
  fetched_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
