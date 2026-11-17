-- Chat account identity (changeset 003).
--
-- The original design recognised a visitor by device alone. Two signed-in
-- accounts on one browser share local storage, so they shared one device id,
-- one conversation and one live stream: micro09 read micro08's messages.
-- Identity is now account-first; the device ladder only serves anonymous
-- visitors, and only among conversations that have no owner.

ALTER TABLE chat_conversation ADD COLUMN owner_username VARCHAR(64) NULL;
ALTER TABLE chat_conversation ADD COLUMN owner_display_name VARCHAR(120) NULL;
-- Capability token for the visitor's event stream. The stream URL cannot carry
-- a JWT (EventSource), and a device id is shared between accounts on one
-- browser, so the stream is keyed by this per-conversation secret instead.
ALTER TABLE chat_conversation ADD COLUMN stream_key VARCHAR(64) NULL;

CREATE INDEX idx_chat_conversation_owner ON chat_conversation(owner_username, id);
