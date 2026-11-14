-- Changeset 028: store the generated VietQR Quick Link on the payment, and the
-- bank BIN on the transfer account so the QR can be built without guessing.
--
-- payment.qr_url      : full img.vietqr.io Quick Link image URL, linked to the
--                       payment code; the seller payment page renders it.
-- payment.qr_add_info : the transfer content (username + plan) baked into the QR.
-- transfer_info.bank_bin : NAPAS BIN of the chosen bank (e.g. 970436), used as
--                       the VietQR BANK_ID. Nullable: older rows predate it and
--                       the generator falls back to the issuer name.

ALTER TABLE payment
    ADD COLUMN qr_url VARCHAR(1000) NULL,
    ADD COLUMN qr_add_info VARCHAR(100) NULL;

ALTER TABLE transfer_info
    ADD COLUMN bank_bin VARCHAR(20) NULL;
