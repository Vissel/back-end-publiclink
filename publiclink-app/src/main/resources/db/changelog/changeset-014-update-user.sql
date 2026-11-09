ALTER TABLE user DROP COLUMN has_authentication;
ALTER TABLE user ADD COLUMN authentication_method VARCHAR(50);
