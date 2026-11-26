-- The application stores product images in picture.data (base64) and leaves
-- picture.link empty; it never writes a URL there. The rebuilt schema from
-- changeset 001 declared link NOT NULL, so saving a product image failed with
-- "Column 'link' cannot be null". The pre-rebuild database already had link
-- nullable, so restore that contract instead of changing the application.
ALTER TABLE picture MODIFY COLUMN link VARCHAR(255) NULL;
