-- Move the authentication service off the shared newuser02 MySQL account.
--
-- Why: publiclink-app (application-qa.properties) and authen-authorisation
-- (src/main/resources/application.properties) both connect as newuser02, so
-- the two JVMs share one set of per-account hourly resource limits
-- (max_questions / max_updates / max_connections) and one MAX_USER_CONNECTIONS
-- cap. Exhausting either makes every statement from BOTH services fail,
-- typically as
--   "User 'newuser02' has exceeded the 'max_questions' resource (current value: 1000)"
-- and a second JVM can also starve on the connection cap outright.
--
-- Run with an admin account (root) against the MySQL/MariaDB host that the
-- authen service's spring.datasource.url points at. Replace the placeholder
-- password; never commit a real credential into this file.

-- 1. Dedicated service account, no hourly quotas (0 = unlimited).
CREATE USER IF NOT EXISTS 'publiclink_authen'@'localhost'
  IDENTIFIED BY 'REPLACE_ME_WITH_A_STRONG_PASSWORD';

ALTER USER 'publiclink_authen'@'localhost'
  WITH MAX_QUERIES_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0
       MAX_CONNECTIONS_PER_HOUR 0 MAX_USER_CONNECTIONS 0;

-- 2. Least privilege. The service only reads and writes rows in publiclink-db
--    and runs no Liquibase changelog, so no DDL is granted. The database name
--    contains a hyphen, so it must be backticked.
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE,
      LOCK TABLES, SHOW VIEW, CREATE TEMPORARY TABLES
  ON `publiclink-db`.* TO 'publiclink_authen'@'localhost';

-- 3. Lift the human-style quotas off the app account too: it is a service, and
--    a restart storm spends the 1000 statements quickly (pool session-init
--    statements plus the Liquibase checksum checks run on every boot).
ALTER USER 'newuser02'@'localhost'
  WITH MAX_QUERIES_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0
       MAX_CONNECTIONS_PER_HOUR 0 MAX_USER_CONNECTIONS 0;
-- Repeat for every host the account is defined for, e.g. 'newuser02'@'%'.

FLUSH PRIVILEGES;

-- 4. Point the authen service at its new account: the two datasource
--    credential lines in
--    authen-authorisation/src/main/resources/application.properties
--      spring.datasource.username=publiclink_authen
--      spring.datasource.password=<the password from step 1>
--    Prefer environment variables over literals here, as the prod profile
--    already does. Note the value currently in that file (newuser002) does not
--    match the qa profile's password for newuser02 (newuser02pass): confirm
--    which credential the server actually accepts before changing it.
--    Then rebuild and restart the service:
--      mvn -B -pl authen-authorisation -am package -DskipTests

-- Verify the limits landed and the grants are scoped to one database.
SELECT user, host, max_questions, max_updates, max_connections, max_user_connections
  FROM mysql.user
 WHERE user IN ('newuser02', 'publiclink_authen');

SHOW GRANTS FOR 'publiclink_authen'@'localhost';

-- Per-account usage counters are not exposed by MySQL; a quota error names the
-- account. To clear the counters immediately instead of waiting for the hour:
--   FLUSH USER_RESOURCES;
