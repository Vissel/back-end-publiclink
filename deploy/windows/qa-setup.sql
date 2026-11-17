-- PublicLink QA on the Windows machine - idempotent MySQL setup.
-- Run once as root, e.g.:
--   mysql -u root -p < C:\publiclink\qa-setup.sql
--
-- Creates the two schemas the Windows services own (user_schema for the user
-- JAR, chat_schema for the chat JAR; each service migrates its own tables).
-- publiclink-db stays on the Mac and is NOT created here.
-- Both JARs connect from localhost only, so no remote grants are needed.

CREATE DATABASE IF NOT EXISTS user_schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS chat_schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Shared QA account, matching the credentials in application-qa.properties of
-- user and chat-app. The ALTER makes sure the password matches even when the
-- account already existed with a different one.
CREATE USER IF NOT EXISTS 'newuser03'@'localhost' IDENTIFIED BY 'Newuser00#';
ALTER USER 'newuser03'@'localhost' IDENTIFIED BY 'Newuser00#';

GRANT ALL PRIVILEGES ON user_schema.* TO 'newuser03'@'localhost';
GRANT ALL PRIVILEGES ON chat_schema.* TO 'newuser03'@'localhost';
FLUSH PRIVILEGES;
